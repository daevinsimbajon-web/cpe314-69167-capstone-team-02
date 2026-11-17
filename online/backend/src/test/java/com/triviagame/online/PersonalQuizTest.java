package com.triviagame.online;

import com.fasterxml.jackson.databind.JsonNode;
import com.triviagame.api.OpenTdbClient;
import com.triviagame.online.rooms.RoomService;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalQuizTest extends RoomTestSupport {
    @MockitoBean OpenTdbClient upstream;
    @Autowired RoomService rooms;
    @Test void customQuestionsAndHostingDoNotRequireOrExposeDifficulty()throws Exception{
        var user=registered(username());var question=new LinkedHashMap<>(tf("Custom statement without difficulty"));question.remove("difficulty");
        var created=user.post("/quizzes",save("READY",0,List.of(question)));assertThat(created.status()).isEqualTo(201);assertThat(created.body().get("questions").get(0).has("difficulty")).isFalse();String id=created.body().get("quiz").get("id").asText();
        var hosted=user.post("/rooms",Map.of("title","Custom game","questionSource","LOCAL","quizId",id,"questionType","any","questionCount",1,"secondsPerQuestion",20,"maxPlayers",2));assertThat(hosted.status()).as(hosted.body().toString()).isEqualTo(201);assertThat(user.get("/quizzes/library").body().get(0).get("question").has("difficulty")).isFalse();
        question.put("difficulty","obsolete custom setting");assertThat(update(user,id,save("READY",1,List.of(question))).status()).isEqualTo(200);
    }
    @Test void jsonImportsPreparePrivateDraftQuestionsWithoutPollutingTheSharedBank()throws Exception{
        var user=registered(username());int before=jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class);
        var imported=user.post("/quizzes/import",Map.of("json",json.writeValueAsString(List.of(Map.of("category","Science","text","The Sun is a star.","correctAnswer","true","incorrectAnswers",List.of("false"),"questionType","TRUE_FALSE")))));
        assertThat(imported.status()).isEqualTo(200);assertThat(imported.body().get(0).get("correctIndex").asInt()).isZero();assertThat(imported.body().get(0).get("options").get(0).asText()).isEqualTo("True");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isEqualTo(before);assertThat(user.post("/quizzes/import",Map.of("json","{broken")).status()).isEqualTo(400);
    }
    Map<String,Object> mc(String text,int correct){return Map.of("type","MULTIPLE_CHOICE","text",text,"options",List.of("Earth","Mars","Venus","Jupiter","Saturn","Neptune"),"correctIndex",correct,"difficulty","easy");}
    Map<String,Object> tf(String text){return Map.of("type","TRUE_FALSE","text",text,"options",List.of("True","False"),"correctIndex",0,"difficulty","easy");}
    Map<String,Object> save(String status,int version,List<?> questions){return new LinkedHashMap<>(Map.of("title","Science Quiz Night","description","A private science collection","status",status,"version",version,"questions",questions));}
    Result update(Browser user,String id,Map<String,Object> input)throws Exception{return user.send("PUT","/quizzes/"+id,input,false,true);}
    String quiz(Browser host,List<?> questions)throws Exception{var result=host.post("/quizzes",save("READY",0,questions));assertThat(result.status()).as(result.body().toString()).isEqualTo(201);return result.body().get("quiz").get("id").asText();}
    String room(Browser host,String quiz)throws Exception{
        var input=new LinkedHashMap<String,Object>(Map.of("title","Private saved quiz","questionSource","LOCAL","quizId",quiz,"difficulty","any","questionType","any","questionCount",1,"secondsPerQuestion",20,"maxPlayers",3,"retryAllowance",3));
        var created=host.post("/rooms",input);assertThat(created.status()).as(created.body().toString()).isEqualTo(201);return created.body().get("code").asText();
    }
    @Test void draftsPersonalCategoriesAndOwnershipAreEnforced()throws Exception{
        var a=registered(username());var b=registered(username());var category=a.post("/quizzes/categories",Map.of("name","  Science  "));assertThat(category.status()).isEqualTo(201);String categoryId=category.body().get("id").asText();
        assertThat(a.post("/quizzes/categories",Map.of("name","SCIENCE")).body().get("id").asText()).isEqualTo(categoryId);
        assertThat(b.get("/quizzes/categories").body()).isEmpty();
        var draft=save("DRAFT",0,List.of(Map.of("type","MULTIPLE_CHOICE","text","","options",List.of("",""),"difficulty","easy")));draft.put("categoryId",categoryId);
        var created=a.post("/quizzes",draft);assertThat(created.status()).isEqualTo(201);String id=created.body().get("quiz").get("id").asText();
        assertThat(created.body().get("quiz").get("visibility").asText()).isEqualTo("PRIVATE");assertThat(a.get("/quizzes/"+id).body().get("questions").size()).isEqualTo(1);
        assertThat(b.get("/quizzes").body()).isEmpty();assertThat(b.get("/quizzes/"+id).status()).isEqualTo(404);assertThat(b.delete("/quizzes/"+id).status()).isEqualTo(404);
        assertThat(b.post("/quizzes/"+id+"/duplicate",Map.of()).status()).isEqualTo(404);assertThat(update(b,id,save("READY",1,List.of(tf("Private")))).status()).isEqualTo(404);
        var foreign=save("DRAFT",0,List.of());foreign.put("categoryId",categoryId);assertThat(b.post("/quizzes",foreign).status()).isEqualTo(400);
        assertThat(a.post("/rooms",Map.of("title","Draft","questionSource","LOCAL","quizId",id,"difficulty","any","questionType","any","questionCount",1,"secondsPerQuestion",20,"maxPlayers",2)).status()).isEqualTo(400);
    }
    @Test void readyValidationRejectsMissingOrAmbiguousAnswersAndInvalidTypes()throws Exception{
        var user=registered(username());assertThat(user.post("/quizzes",save("READY",0,List.of())).status()).isEqualTo(400);
        for(String property:List.of("text","options","correctIndex","type")){
            var invalid=new LinkedHashMap<>(mc("Planet?",0));if(property.equals("text"))invalid.put(property,"");if(property.equals("options"))invalid.put(property,List.of("Earth"," earth "));if(property.equals("correctIndex"))invalid.remove(property);if(property.equals("type"))invalid.put(property,"mixed");
            assertThat(user.post("/quizzes",save("READY",0,List.of(invalid))).status()).as(property).isEqualTo(400);
        }
        for(List<String> options:List.of(List.of("one"),List.of("one",""),List.of("one","two","three","four","five","six","seven"))){var invalid=new LinkedHashMap<>(mc("Text",0));invalid.put("options",options);assertThat(user.post("/quizzes",save("READY",0,List.of(invalid))).status()).isEqualTo(400);}
        var wrongBoolean=new LinkedHashMap<>(tf("Sun is a star"));wrongBoolean.put("options",List.of("yes","no"));assertThat(user.post("/quizzes",save("READY",0,List.of(wrongBoolean))).status()).isEqualTo(400);
        var fractional=new LinkedHashMap<>(tf("Sun is a star"));fractional.put("correctIndex",0.5);assertThat(user.post("/quizzes",save("READY",0,List.of(fractional))).status()).isEqualTo(400);
        assertThat(user.post("/quizzes",save("READY",0,List.of(tf("Sun is a star"),mc("Planet?",1)))).status()).isEqualTo(201);
    }
    @Test void quizEditsReorderSafelyReuseQuestionsAndRejectStaleVersions()throws Exception{
        var user=registered(username());String id=quiz(user,List.of(mc("Which planet?",1),tf("Sun is a star")));
        var original=user.get("/quizzes/"+id).body();assertThat(original.get("quiz").get("multipleChoiceCount").asInt()).isEqualTo(1);assertThat(original.get("quiz").get("trueFalseCount").asInt()).isEqualTo(1);
        assertThat(user.get("/quizzes/library").body().size()).isEqualTo(2);
        var reused=new LinkedHashMap<>(mc("Which planet?",0));reused.put("options",List.of("Mars","Earth","Venus","Jupiter","Saturn","Neptune"));
        assertThat(user.post("/quizzes",save("READY",0,List.of(reused))).status()).isEqualTo(201);assertThat(user.get("/quizzes/library").body().size()).isEqualTo(2);
        var updated=update(user,id,save("READY",1,List.of(tf("Sun is a star"),mc("Which planet?",1))));assertThat(updated.status()).as(updated.body().toString()).isEqualTo(200);assertThat(updated.body().get("questions").get(0).get("type").asText()).isEqualTo("TRUE_FALSE");
        assertThat(user.get("/quizzes/library").body().size()).isEqualTo(2);
        assertThat(update(user,id,save("READY",1,List.of(tf("Stale")))).status()).isEqualTo(409);
        var copy=user.post("/quizzes/"+id+"/duplicate",Map.of());assertThat(copy.status()).isEqualTo(201);assertThat(copy.body().get("quiz").get("status").asText()).isEqualTo("DRAFT");
        assertThat(user.delete("/quizzes/"+id).status()).isEqualTo(204);assertThat(user.get("/quizzes/"+id).status()).isEqualTo(404);assertThat(user.get("/quizzes/library").body().size()).isEqualTo(2);
    }
    @Test void mixedQuizPlaysInOrderWithRetriesAndImmutableSnapshots()throws Exception{
        var host=registered(username());var player=registered(username());String id=quiz(host,List.of(mc("Original planet question",1),tf("The Sun is a star.")));
        assertThat(player.get("/quizzes/library").body()).isEmpty();
        assertThat(player.post("/rooms",Map.of("title","Unauthorized","questionSource","LOCAL","quizId",id,"difficulty","any","questionType","any","questionCount",2,"secondsPerQuestion",20,"maxPlayers",2)).status()).isEqualTo(404);
        String code=room(host,id),path="/rooms/"+code;assertThat(host.get(path).body().get("questionCount").asInt()).isEqualTo(2);join(player,code);
        assertThat(update(host,id,save("READY",1,List.of(tf("Changed later")))).status()).isEqualTo(200);
        assertThat(host.post(path+"/start",Map.of()).status()).isEqualTo(200);clock.advance(Duration.ofSeconds(5));rooms.tick(code);var first=player.get(path).body().get("question");assertThat(first.get("text").asText()).isEqualTo("Original planet question");assertThat(first.get("choices").size()).isEqualTo(6);assertThat(first.get("correctAnswer").isNull()).isTrue();
        int correct=correctIndex(code,first);var deadline=player.get(path).body().get("deadline");var result=player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",(correct+1)%6));assertThat(result.body().get("outcome").asText()).isEqualTo("RETRY");assertThat(player.post(path+"/retry",Map.of("submissionId",result.body().get("submissionId").asText(),"useChance",true)).status()).isEqualTo(200);clock.advance(Duration.ofMillis(350));
        assertThat(player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",correct)).body().get("awardedPoints").asInt()).isEqualTo(7);assertThat(player.get(path).body().get("deadline")).isEqualTo(deadline);
        clock.advance(Duration.ofMillis(1700));rooms.tick(code);
        var second=player.get(path).body();assertThat(second.get("question").get("type").asText()).isEqualTo("TRUE_FALSE");assertThat(second.get("retriesRemaining").asInt()).isEqualTo(2);
        assertThat(player.post(path+"/answers",Map.of("questionNumber",2,"selectedIndex",correctIndex(code,second.get("question")))).body().get("awardedPoints").asInt()).isEqualTo(10);
        host.post(path+"/end",Map.of());assertThat(host.delete("/quizzes/"+id).status()).isEqualTo(204);
        JsonNode game=player.get("/analytics/export").body().get("games").get(0);assertThat(game.get("pointsEarned").asInt()).isEqualTo(17);assertThat(game.get("maxPossiblePoints").asInt()).isEqualTo(20);assertThat(game.toString()).doesNotContain("correctAnswer","correct_answer");assertThat(player.get("/history").body().size()).isEqualTo(1);
        verifyNoInteractions(upstream);
    }
    @Test void duplicatedQuestionsCanBeHostedWithoutDuplicatingImmutableLibraryRows()throws Exception{
        var host=registered(username());String id=quiz(host,List.of(tf("A repeated statement"),tf("A repeated statement")));String code=room(host,id);assertThat(host.get("/rooms/"+code).body().get("questionCount").asInt()).isEqualTo(2);assertThat(host.get("/quizzes/library").body().size()).isEqualTo(1);
    }
}
