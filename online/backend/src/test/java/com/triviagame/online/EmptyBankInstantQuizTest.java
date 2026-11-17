package com.triviagame.online;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
/** A separate empty database, not a destructive deletion of any application bank. */
@org.springframework.test.context.TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:emptyinstant;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class EmptyBankInstantQuizTest extends HttpTestSupport {
    static final HttpServer provider=provider();
    @Autowired JdbcTemplate jdbc;
    static HttpServer provider(){try{var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",request->{String body=request.getRequestURI().getPath().contains("category")?"{\"trivia_categories\":[{\"id\":18,\"name\":\"Science\"}]}":"{\"response_code\":0,\"results\":[{\"category\":\"Science\",\"question\":\"The%20Sun%20is%20a%20star.\",\"type\":\"boolean\",\"difficulty\":\"easy\",\"correct_answer\":\"True\",\"incorrect_answers\":[\"False\"]}]}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);request.sendResponseHeaders(200,bytes.length);request.getResponseBody().write(bytes);request.close();});server.start();return server;}catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry config){config.add("app.seed-example-bank",()->false);config.add("app.opentdb.endpoint",()->"http://127.0.0.1:"+provider.getAddress().getPort()+"/api.php");config.add("app.opentdb.interval-ms",()->0);}
    @AfterAll static void closeProvider(){provider.stop(0);}
    @Test void freshProviderQuestionsCreateAStableRoomWithZeroBankRecords()throws Exception{
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isZero();var host=registered(username());
        var result=host.post("/rooms",Map.of("title","Zero bank test","questionSource","OPENTDB","difficulty","easy","questionType","boolean","questionCount",1,"secondsPerQuestion",10,"maxPlayers",2));assertThat(result.status()).as(result.body().toString()).isEqualTo(201);String code=result.body().get("code").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room_questions q JOIN quiz_rooms r ON r.id=q.room_id WHERE r.code=?",Integer.class,code)).isEqualTo(1);assertThat(result.body().get("independentFlow").asBoolean()).isTrue();assertThat(host.get("/rooms/"+code).body().get("questionCount").asInt()).isEqualTo(1);
    }
}
