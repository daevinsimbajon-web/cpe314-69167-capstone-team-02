package com.triviagame.online;

import com.fasterxml.jackson.databind.JsonNode;
import com.triviagame.online.rooms.RoomService;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class RoomGameTest extends RoomTestSupport {
    @Autowired RoomService rooms;
    @Test void completeHostPlayerScenarioPersistsFinalScoresAndHistory() throws Exception {
        Browser host=registered(username()),player=registered(username());String code=create(host,2,4),path="/rooms/"+code;
        assertThat(join(player,code).status()).isEqualTo(200);
        assertThat(join(player,code).body().get("participants").size()).isEqualTo(2);
        assertThat(host.get(path).body().get("participants").size()).isEqualTo(2);
        assertThat(host.post(path+"/start",null).status()).isEqualTo(200);
        int expected=0;
        for(int number=1;number<=2;number++) {
            JsonNode question=player.get(path).body().get("question");
            assertThat(question.get("number").asInt()).isEqualTo(number);
            assertThat(host.get(path).body().get("question")).isEqualTo(question);
            assertThat(question.get("correctAnswer").isNull()).isTrue();
            Result answer=player.post(path+"/answers",Map.of("questionNumber",number,"selectedIndex",correctIndex(code,question)));
            assertThat(answer.status()).isEqualTo(202);
            assertThat(answer.body().has("correct")).isFalse();
            assertThat(player.get(path).body().get("leaderboard").get(0).get("points").asInt()).isEqualTo(expected);
            expected+=10;
            clock.advance(Duration.ofSeconds(5));rooms.tick(code);
            JsonNode reveal=player.get(path).body();
            assertThat(reveal.get("status").asText()).isEqualTo("REVEAL");
            assertThat(reveal.get("question").get("correctAnswer").isTextual()).isTrue();
            assertThat(reveal.get("leaderboard").get(0).get("points").asInt()).isEqualTo(expected);
            clock.advance(Duration.ofSeconds(5));rooms.tick(code);
        }
        assertThat(player.get(path).body().get("status").asText()).isEqualTo("FINISHED");
        JsonNode history=player.get("/history").body();
        assertThat(history.get(0).get("points").asInt()).isEqualTo(expected);
        assertThat(history.get(0).get("roomCode").asText()).isEqualTo(code);
        assertThat(player.get("/me").body().get("statistics").get("gamesPlayed").asInt()).isEqualTo(1);
        assertThat(player.get("/analytics").body().isEmpty()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_sessions g JOIN quiz_rooms r ON r.id=g.room_id WHERE r.code=? AND g.finished_at IS NOT NULL",Integer.class,code)).isEqualTo(1);
    }
    @Test void onlyHostCanControlRoomAndOutsidersCannotReadIt() throws Exception {
        Browser host=registered(username()),player=registered(username()),outsider=registered(username());
        String code=create(host,1,3),path="/rooms/"+code;join(player,code);
        for(String operation:List.of("start","next","end"))assertThat(player.post(path+"/"+operation,null).status()).isEqualTo(403);
        assertThat(outsider.get(path).status()).isEqualTo(403);
        assertThat(outsider.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",0)).status()).isEqualTo(403);
    }
    @Test void concurrentJoinsRespectCapacityAndDuplicateJoinIsIdempotent() throws Exception {
        Browser host=registered(username()),a=registered(username()),b=registered(username());String code=create(host,1,2);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<Result> first=executor.submit(()->join(a,code)),second=executor.submit(()->join(b,code));
            Result one=first.get(15,TimeUnit.SECONDS),two=second.get(15,TimeUnit.SECONDS);
            assertThat(List.of(one.status(),two.status())).containsExactlyInAnyOrder(200,409);
            Browser joined=one.status()==200?a:b;
            assertThat(join(joined,code).status()).isEqualTo(200);
            assertThat(host.get("/rooms/"+code).body().get("participants").size()).isEqualTo(2);
        }finally{executor.shutdownNow();assertThat(executor.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void finalAnswersCannotBeUndoneAndLateAnswersAreRejected() throws Exception {
        Browser host=registered(username()),player=registered(username());String code=create(host,1,3),path="/rooms/"+code;join(player,code);host.post(path+"/start",null);
        JsonNode question=player.get(path).body().get("question");Map<String,Integer> answer=Map.of("questionNumber",1,"selectedIndex",correctIndex(code,question));
        assertThat(player.post(path+"/answers",answer).status()).isEqualTo(202);
        assertThat(player.post(path+"/answers",answer).status()).isEqualTo(409);
        assertThat(player.delete(path+"/answers/1").status()).isEqualTo(409);
        assertThat(player.post(path+"/answers",answer).status()).isEqualTo(409);
        clock.advance(Duration.ofSeconds(5));
        assertThat(player.post(path+"/answers",answer).status()).isEqualTo(409);
        assertThat(player.delete(path+"/answers/1").status()).isEqualTo(409);
        rooms.tick(code);int score=player.get(path).body().get("leaderboard").get(0).get("points").asInt();
        rooms.tick(code);assertThat(player.get(path).body().get("leaderboard").get(0).get("points").asInt()).isEqualTo(score);
    }
    @Test void rejectsClientScoresInvalidChoicesAndStartWithoutPlayers() throws Exception {
        Browser host=registered(username()),player=registered(username());String code=create(host,1,3),path="/rooms/"+code;
        assertThat(host.post(path+"/start",null).status()).isEqualTo(409);join(player,code);host.post(path+"/start",null);
        assertThat(player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",0,"points",9999)).status()).isEqualTo(400);
        assertThat(player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",10)).status()).isEqualTo(400);
        assertThat(player.post(path+"/answers",Map.of("questionNumber",2,"selectedIndex",0)).status()).isEqualTo(409);
        assertThat(host.post(path+"/next",null).status()).isEqualTo(409);
    }
    @Test void roomPasswordRemovalAndExpirationAreEnforced() throws Exception {
        Browser host=registered(username()),player=registered(username());
        Result result=host.post("/rooms",Map.of("title","Private room","difficulty","any","questionType","any","questionCount",1,
                "secondsPerQuestion",5,"maxPlayers",3,"password","PrivateRoom42"));
        assertThat(result.status()).isEqualTo(201);String code=result.body().get("code").asText(),path="/rooms/"+code;
        assertThat(join(player,code).status()).isEqualTo(403);
        assertThat(player.post(path+"/join",Map.of("password","PrivateRoom42")).status()).isEqualTo(200);
        String playerId=player.get("/me").body().get("id").asText();
        assertThat(host.delete(path+"/participants/"+playerId).status()).isEqualTo(204);
        assertThat(player.get(path).status()).isEqualTo(403);
        clock.advance(Duration.ofHours(7));rooms.tick(code);
        assertThat(player.post(path+"/join",Map.of("password","PrivateRoom42")).status()).isEqualTo(409);
        assertThat(host.get(path).body().get("status").asText()).isEqualTo("CLOSED");
    }
    @Test void removedLobbyVisitorCannotReadResultsButAFormerPlayerCan() throws Exception {
        Browser host=registered(username()),player=registered(username()),visitor=registered(username());
        String code=create(host,1,3),path="/rooms/"+code;
        assertThat(join(player,code).status()).isEqualTo(200);
        assertThat(join(visitor,code).status()).isEqualTo(200);
        String visitorId=visitor.get("/me").body().get("id").asText();
        assertThat(host.delete(path+"/participants/"+visitorId).status()).isEqualTo(204);
        assertThat(host.post(path+"/start",null).status()).isEqualTo(200);
        assertThat(host.post(path+"/end",null).status()).isEqualTo(200);
        assertThat(visitor.get(path).status()).isEqualTo(403);
        assertThat(player.post(path+"/leave",null).status()).isEqualTo(204);
        assertThat(player.get(path).status()).isEqualTo(200);
        assertThat(player.get("/history").body().get(0).get("roomCode").asText()).isEqualTo(code);
    }
}
