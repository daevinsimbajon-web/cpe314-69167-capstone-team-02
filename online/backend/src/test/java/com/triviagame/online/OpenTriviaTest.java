package com.triviagame.online;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.triviagame.online.rooms.RoomService;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Controlled HTTP upstream; no unit test needs the real internet. */
class OpenTriviaTest extends RoomTestSupport {
    static final String CATEGORIES="{\"trivia_categories\":[{\"id\":18,\"name\":\"Science: Computers\"},{\"id\":23,\"name\":\"History\"}]}";
    static final HttpServer server=startServer();
    static volatile String categoryBody=CATEGORIES,mode="success",query="";
    static final java.util.concurrent.atomic.AtomicInteger questionRequests=new java.util.concurrent.atomic.AtomicInteger();
    @Autowired RoomService rooms;
    static HttpServer startServer(){try{
        var fake=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        fake.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(task->{var thread=new Thread(task);thread.setDaemon(true);return thread;}));
        fake.createContext("/",exchange->{try{
            String body;int status=200;
            if(exchange.getRequestURI().getPath().equals("/api_category.php"))body=categoryBody;
            else{
                questionRequests.incrementAndGet();
                query=exchange.getRequestURI().getRawQuery();var values=new HashMap<String,String>();for(String pair:query.split("&")){var parts=pair.split("=",2);values.put(parts[0],parts[1]);}
                if(mode.equals("slow"))Thread.sleep(1300);
                if(mode.equals("http")){status=503;body="unavailable";}
                else if(mode.equals("malformed"))body="{broken";
                else if(!mode.equals("success")&&!mode.equals("slow"))body="{\"response_code\":"+mode+",\"results\":[]}";
                else{
                    var questions=new ArrayList<Map<String,Object>>();String type=values.getOrDefault("type","multiple"),category=values.getOrDefault("category","18").equals("23")?"History":"Science: Computers";
                    for(int i=0;i<Integer.parseInt(values.get("amount"));i++)questions.add(Map.of("category",encode(category),"question",encode("Controlled source question "+i+" "+type),"type",type,"difficulty",values.getOrDefault("difficulty","medium"),"correct_answer",type.equals("boolean")?"True":"A","incorrect_answers",type.equals("boolean")?List.of("False"):List.of("B","C","D")));
                    body=new ObjectMapper().writeValueAsString(Map.of("response_code",0,"results",questions));
                }
            }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{exchange.close();}});fake.start();return fake;
    }catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
    static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    @DynamicPropertySource static void upstream(DynamicPropertyRegistry registry){registry.add("app.opentdb.endpoint",()->"http://127.0.0.1:"+server.getAddress().getPort()+"/api.php");registry.add("app.opentdb.timeout-ms",()->700);registry.add("app.opentdb.interval-ms",()->0);}
    @BeforeEach void reset(){mode="success";categoryBody=CATEGORIES;questionRequests.set(0);jdbc.update("DELETE FROM opentdb_category_cache");}
    @Autowired com.triviagame.api.OpenTdbClient client;
    @Test void localRateGateRequiresFiveSecondsBetweenQuestionRequests(){
        var paced=new com.triviagame.online.bank.OpenTriviaService(client,jdbc,json,clock,5000);
        assertThat(paced.fetch(1,null,"any","any")).hasSize(1);
        assertThatThrownBy(()->paced.fetch(1,null,"any","any")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("429");
        clock.advance(Duration.ofSeconds(5));assertThat(paced.fetch(1,null,"any","any")).hasSize(1);
    }
    @Test void malformedCategoryCachesCannotBecomeOfficialLists(){
        for(String invalid:List.of("{\"trivia_categories\":[]}","{\"trivia_categories\":[{\"id\":\"18\",\"name\":\"Science\"}]}","{\"trivia_categories\":[{\"id\":18,\"name\":\" \"}]}","{\"trivia_categories\":[{\"id\":18,\"name\":\"Science\"},{\"id\":18,\"name\":\"Other\"}]}"))
            assertThatThrownBy(()->com.triviagame.api.OpenTdbClient.parseCategories(invalid)).isInstanceOf(java.io.IOException.class);
    }
    @AfterAll static void stop(){server.stop(0);}
    Map<String,Object> request(){var input=new LinkedHashMap<String,Object>();input.put("title","Official source test");input.put("questionSource","OPENTDB");input.put("openTdbCategoryId",18);input.put("difficulty","medium");input.put("questionType","multiple");input.put("questionCount",10);input.put("secondsPerQuestion",10);input.put("maxPlayers",3);input.put("retryAllowance",3);return input;}
    @Test void officialCategoriesNeverContainLocalLabelsAndValidatedCacheSurvivesOutage() throws Exception {
        var host=registered(username());jdbc.update("INSERT INTO categories(name,normalized_name) VALUES(?,?)","Owl reactions test record","owl reactions test record");
        var result=host.get("/bank/opentdb/categories");assertThat(result.status()).isEqualTo(200);assertThat(result.body().get("categories").toString()).contains("Science: Computers","18").doesNotContain("Owl reactions");
        clock.advance(Duration.ofHours(25));categoryBody="{\"trivia_categories\":[]}";var cached=host.get("/bank/opentdb/categories");assertThat(cached.status()).isEqualTo(200);assertThat(cached.body().get("cached").asBoolean()).isTrue();
        jdbc.update("UPDATE opentdb_category_cache SET document='broken'");assertThat(host.get("/bank/opentdb/categories").status()).isEqualTo(503);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE name='Owl reactions test record'",Integer.class)).isEqualTo(1);
    }
    @Test void selectedFiltersArePersistedAndUsedAndTwoPlayersReceiveIndependentRetries() throws Exception {
        var host=registered(username());var a=registered(username());var b=registered(username());
        int bankBefore=jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class),categoriesBefore=jdbc.queryForObject("SELECT COUNT(*) FROM categories",Integer.class),importsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM question_bank_imports",Integer.class);
        var created=host.post("/rooms",request());assertThat(created.status()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isEqualTo(bankBefore);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories",Integer.class)).isEqualTo(categoriesBefore);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_bank_imports",Integer.class)).isEqualTo(importsBefore);
        assertThat(query).contains("amount=10","category=18","difficulty=medium","type=multiple");String code=created.body().get("code").asText(),path="/rooms/"+code;
        assertThat(created.body().get("openTdbCategoryId").asInt()).isEqualTo(18);assertThat(created.body().get("questionSource").asText()).isEqualTo("OPENTDB");assertThat(created.body().get("questionType").asText()).isEqualTo("multiple");
        join(a,code);join(b,code);host.post(path+"/start",null);clock.advance(Duration.ofSeconds(5));rooms.tick(code);assertThat(a.get(path).body().get("retriesRemaining").asInt()).isEqualTo(3);assertThat(b.get(path).body().get("retriesRemaining").asInt()).isEqualTo(3);
        var q=a.get(path).body().get("question");assertThat(q.get("difficulty").asText()).isEqualTo("medium");assertThat(q.get("type").asText()).isEqualTo("MULTIPLE_CHOICE");assertThat(q.get("correctAnswer").isNull()).isTrue();
        a.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",correctIndex(code,q)));clock.advance(Duration.ofMillis(1700));rooms.tick(code);
        q=a.get(path).body().get("question");int index=correctIndex(code,q);var deadline=a.get(path).body().get("deadline");var wrong=a.post(path+"/answers",Map.of("questionNumber",2,"selectedIndex",(index+1)%4));assertThat(wrong.body().get("pointsAvailable").asInt()).isEqualTo(10);assertThat(wrong.body().get("retriesRemaining").asInt()).isEqualTo(3);assertThat(a.post(path+"/retry",Map.of("submissionId",wrong.body().get("submissionId").asText(),"useChance",true)).status()).isEqualTo(200);
        clock.advance(Duration.ofMillis(350));assertThat(a.post(path+"/answers",Map.of("questionNumber",2,"selectedIndex",index)).body().get("awardedPoints").asInt()).isEqualTo(7);assertThat(a.get(path).body().get("deadline")).isEqualTo(deadline);assertThat(b.get(path).body().get("retriesRemaining").asInt()).isEqualTo(3);host.post(path+"/end",null);
        assertThat(a.get(path).body().get("leaderboard").get(0).get("points").asInt()).isEqualTo(17);var game=a.get("/analytics/export").body().get("games").get(0);assertThat(game.get("pointsEarned").asInt()).isEqualTo(17);assertThat(game.get("maxPossiblePoints").asInt()).isEqualTo(20);assertThat(game.get("questions").get(1).get("retriesUsed").asInt()).isEqualTo(1);
        assertThat(game.get("questions").get(0).get("category").asText()).isEqualTo("Science: Computers");assertThat(a.get("/analytics").body().get(0).get("category").asText()).isEqualTo("Science: Computers");assertThat(questionRequests.get()).isEqualTo(1);
    }
    @Test void instantQuestionsAreGameSpecificAndNeverEnterPersonalCollections()throws Exception{
        var host=registered(username());var player=registered(username());
        var authored=host.post("/quizzes",Map.of("title","My own quiz","status","READY","version",0,"questions",List.of(Map.of("type","TRUE_FALSE","text","My authored statement","options",List.of("True","False"),"correctIndex",0,"difficulty","easy"))));assertThat(authored.status()).isEqualTo(201);
        var collection=host.get("/quizzes").body();var library=host.get("/quizzes/library").body();int bankBefore=jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class);
        var input=request();input.put("questionCount",1);input.put("questionType","boolean");
        var first=host.post("/rooms",input);assertThat(first.status()).isEqualTo(201);String code=first.body().get("code").asText(),path="/rooms/"+code;
        UUID firstSnapshot=jdbc.queryForObject("SELECT rq.id FROM room_questions rq JOIN quiz_rooms r ON r.id=rq.room_id WHERE r.code=?",UUID.class,code);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_questions rq JOIN quiz_rooms r ON r.id=rq.room_id WHERE r.code=? AND rq.question_id IS NULL AND rq.category_name='Science: Computers'",Integer.class,code)).isEqualTo(1);
        mode="http";join(player,code);assertThat(host.post(path+"/start",null).status()).isEqualTo(200);clock.advance(Duration.ofSeconds(5));rooms.tick(code);
        var question=player.get(path).body().get("question");assertThat(question.get("correctAnswer").isNull()).isTrue();int correct=correctIndex(code,question);
        var pending=player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",1-correct));assertThat(player.post(path+"/retry",Map.of("submissionId",pending.body().get("submissionId").asText(),"useChance",true)).status()).isEqualTo(200);clock.advance(Duration.ofMillis(350));assertThat(player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",correct)).body().get("awardedPoints").asInt()).isEqualTo(7);host.post(path+"/end",null);
        var exported=player.get("/analytics/export").body().get("games").get(0);assertThat(exported.get("questions").get(0).get("sourceQuestionId").asText()).isEqualTo(firstSnapshot.toString());assertThat(player.get("/analytics").body().get(0).get("answered").asInt()).isEqualTo(1);assertThat(player.get("/me").body().get("statistics").get("totalPoints").asInt()).isEqualTo(7);assertThat(questionRequests.get()).isEqualTo(1);
        mode="success";var second=host.post("/rooms",input);assertThat(second.status()).isEqualTo(201);UUID secondSnapshot=jdbc.queryForObject("SELECT rq.id FROM room_questions rq JOIN quiz_rooms r ON r.id=rq.room_id WHERE r.code=?",UUID.class,second.body().get("code").asText());assertThat(secondSnapshot).isNotEqualTo(firstSnapshot);assertThat(questionRequests.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isEqualTo(bankBefore);assertThat(host.get("/quizzes").body()).isEqualTo(collection);assertThat(host.get("/quizzes/library").body()).isEqualTo(library);assertThat(player.get("/quizzes/library").body()).isEmpty();
    }
    @Test void anyFiltersAreOmittedAndBothSupportedQuestionTypesMapCorrectly() throws Exception {
        var host=registered(username());var input=request();input.remove("openTdbCategoryId");input.put("difficulty","any");input.put("questionType","any");input.put("questionCount",1);input.put("retryAllowance",0);
        assertThat(host.post("/rooms",input).status()).isEqualTo(201);assertThat(query).doesNotContain("category=","difficulty=","type=");
        input.put("questionType","boolean");assertThat(host.post("/rooms",input).status()).isEqualTo(201);assertThat(query).contains("type=boolean");
        for(String difficulty:List.of("easy","medium","hard")){input.put("difficulty",difficulty);assertThat(host.post("/rooms",input).status()).isEqualTo(201);assertThat(query).contains("difficulty="+difficulty);}
    }
    @Test void failuresNeverCreateBrokenRoomsAndInvalidParametersAreRejected() throws Exception {
        var host=registered(username());int before=jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class);
        for(String failure:List.of("1","2","3","4","5","malformed","http","slow")){mode=failure;assertThat(host.post("/rooms",request()).status()).isEqualTo(failure.equals("5")?429:502);}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isEqualTo(before);
        var invalid=request();invalid.put("difficulty","expert");assertThat(host.post("/rooms",invalid).status()).isEqualTo(400);invalid=request();invalid.put("questionType","MULTIPLE_CHOICE");assertThat(host.post("/rooms",invalid).status()).isEqualTo(400);invalid=request();invalid.put("openTdbCategoryId",999);assertThat(host.post("/rooms",invalid).status()).isEqualTo(400);
    }
}
