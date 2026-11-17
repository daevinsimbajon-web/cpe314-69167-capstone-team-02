package com.triviagame.online;

import com.fasterxml.jackson.databind.JsonNode;
import com.triviagame.online.rooms.RoomService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class RetryGameTest extends RoomTestSupport {
    @Autowired RoomService rooms;
    String retryRoom(Browser host,int questions,int retries) throws Exception {
        var result=host.post("/rooms",Map.of("title","Retry test","difficulty","any","questionType","any","questionCount",questions,"secondsPerQuestion",5,"maxPlayers",3,"retryAllowance",retries));
        assertThat(result.status()).isEqualTo(201);return legacyRoom(result.body().get("code").asText());
    }
    Map<String,Object> attempt(JsonNode question,boolean correct,String code,UUID id){
        int index=correctIndex(code,question);if(!correct)index=(index+1)%question.get("choices").size();
        return Map.of("questionNumber",question.get("number").asInt(),"selectedIndex",index,"submissionId",id);
    }
    @Test void wholeGameBalancesLastChanceAndFinalScoresAreIndependent() throws Exception {
        var host=registered(username());var a=registered(username());var b=registered(username());String code=retryRoom(host,2,3),path="/rooms/"+code;
        join(a,code);join(b,code);host.post(path+"/start",null);
        var first=a.get(path).body();var q=first.get("question");var deadline=first.get("deadline");
        assertThat(a.post(path+"/answers",attempt(q,false,code,UUID.randomUUID())).body().get("retriesRemaining").asInt()).isEqualTo(2);
        assertThat(a.get(path).body().get("questionFinalized").asBoolean()).isFalse();
        assertThat(a.get(path).body().get("submittedIndex").isNull()).isTrue();
        assertThat(a.get(path).body().get("question").get("correctAnswer").isNull()).isTrue();
        assertThat(b.get(path).body().get("retriesRemaining").asInt()).isEqualTo(3);
        clock.advance(Duration.ofMillis(350));
        assertThat(a.post(path+"/answers",attempt(q,true,code,UUID.randomUUID())).body().get("outcome").asText()).isEqualTo("CORRECT");
        assertThat(a.get(path).body().get("retriesRemaining").asInt()).isEqualTo(2);
        assertThat(b.post(path+"/answers",attempt(q,false,code,UUID.randomUUID())).body().get("retriesRemaining").asInt()).isEqualTo(2);
        assertThat(a.get(path).body().get("deadline")).isEqualTo(deadline);
        clock.advance(Duration.ofSeconds(5));rooms.tick(code);host.post(path+"/next",null);
        q=a.get(path).body().get("question");
        for(int remaining=1;remaining>=0;remaining--){
            assertThat(a.post(path+"/answers",attempt(q,false,code,UUID.randomUUID())).body().get("retriesRemaining").asInt()).isEqualTo(remaining);
            assertThat(a.get(path).body().get("questionFinalized").asBoolean()).isFalse();clock.advance(Duration.ofMillis(350));
        }
        assertThat(a.post(path+"/answers",attempt(q,false,code,UUID.randomUUID())).body().get("outcome").asText()).isEqualTo("INCORRECT");
        assertThat(a.get(path).body().get("questionFinalized").asBoolean()).isTrue();
        host.post(path+"/end",null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answers a JOIN room_questions q ON q.id=a.room_question_id JOIN quiz_rooms r ON r.id=q.room_id WHERE r.code=?",Integer.class,code)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT SUM(retries_used) FROM answers a JOIN room_questions q ON q.id=a.room_question_id JOIN quiz_rooms r ON r.id=q.room_id WHERE r.code=?",Integer.class,code)).isEqualTo(4);
        assertThat(a.get(path).body().get("retriesRemaining").asInt()).isZero();
        assertThat(b.get(path).body().get("retriesRemaining").asInt()).isEqualTo(2);
        String next=retryRoom(host,1,3);join(a,next);host.post("/rooms/"+next+"/start",null);
        assertThat(a.get("/rooms/"+next).body().get("retriesRemaining").asInt()).isEqualTo(3);
    }
    @Test void zeroRetriesSettingsAuthorizationAndTimeoutUseAuthoritativeClock() throws Exception {
        var host=registered(username());var player=registered(username());String code=retryRoom(host,1,0),path="/rooms/"+code;join(player,code);
        assertThat(player.patch(path+"/settings",Map.of("retryAllowance",3)).status()).isEqualTo(403);
        assertThat(host.patch(path+"/settings",Map.of("retryAllowance",11)).status()).isEqualTo(400);
        assertThat(host.patch(path+"/settings",Map.of("retryAllowance",-1)).status()).isEqualTo(400);
        assertThat(host.patch(path+"/settings",Map.of("retryAllowance",0)).status()).isEqualTo(200);
        host.post(path+"/start",null);
        assertThat(host.patch(path+"/settings",Map.of("retryAllowance",3)).status()).isEqualTo(409);
        var q=player.get(path).body().get("question");
        assertThat(player.post(path+"/answers",attempt(q,false,code,UUID.randomUUID())).body().get("outcome").asText()).isEqualTo("INCORRECT");
        clock.advance(Duration.ofSeconds(5));
        assertThat(host.post(path+"/answers",attempt(q,true,code,UUID.randomUUID())).status()).isEqualTo(409);
        rooms.tick(code);rooms.tick(code);
        assertThat(host.get(path).body().get("retriesRemaining").asInt()).isZero();
        var timed=jdbc.queryForMap("SELECT a.* FROM answers a JOIN room_questions q ON q.id=a.room_question_id JOIN quiz_rooms r ON r.id=q.room_id WHERE r.code=? AND a.timed_out=TRUE",code);
        assertThat(timed.get("points")).isEqualTo(0);assertThat(timed.get("attempt_count")).isEqualTo(0);assertThat(timed.get("first_try_correct")).isNull();assertThat(((Number)timed.get("elapsed_ms")).longValue()).isEqualTo(5000L);
    }
    @Test void concurrentDuplicateSubmissionsConsumeOneChanceAndReplaySurvivesDeadline() throws Exception {
        var host=registered(username());var player=registered(username());String code=retryRoom(host,1,3),path="/rooms/"+code;join(player,code);host.post(path+"/start",null);
        var q=player.get(path).body().get("question");var body=attempt(q,false,code,UUID.randomUUID());
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->player.post(path+"/answers",body));var second=executor.submit(()->player.post(path+"/answers",body));
            var one=first.get(15,TimeUnit.SECONDS);var two=second.get(15,TimeUnit.SECONDS);
            assertThat(one.status()).isEqualTo(202);assertThat(two.status()).isEqualTo(202);assertThat(one.body()).isEqualTo(two.body());
        } finally {executor.shutdownNow();}
        assertThat(player.get(path).body().get("retriesRemaining").asInt()).isEqualTo(2);
        assertThat(player.post(path+"/answers",attempt(q,false,code,UUID.randomUUID())).status()).isEqualTo(409);
        assertThat(player.post(path+"/answers",attempt(q,true,code,(UUID)body.get("submissionId"))).status()).isEqualTo(409);
        clock.advance(Duration.ofSeconds(6));rooms.tick(code);
        assertThat(player.post(path+"/answers",body).body()).isEqualTo(player.get(path).body().get("lastAttempt"));
        assertThat(player.get(path).body().get("retriesRemaining").asInt()).isEqualTo(2);
    }
}
