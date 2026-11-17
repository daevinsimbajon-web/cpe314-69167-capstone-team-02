package com.triviagame.online;

import com.triviagame.online.rooms.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class RetryScoringTest extends RoomTestSupport {
    @Autowired RoomService rooms;
    @Test void scoreFunctionIsBoundedAndHistoricalRulesStayUnchanged(){
        var scoring=new ScoringService();for(int i=0;i<4;i++)assertThat(scoring.calculateCorrectAnswerPoints(i)).isEqualTo(new int[]{10,7,4,2}[i]);
        for(int i:new int[]{-1,4,10})assertThatThrownBy(()->scoring.calculateCorrectAnswerPoints(i)).isInstanceOf(IllegalArgumentException.class);
        assertThat(scoring.correctPoints("retry-v1",3,5)).isEqualTo(5);assertThat(scoring.correctPoints("legacy-v1",0,10)).isEqualTo(10);
    }
    @Test void optionalRetrySettingsDefaultOffAndRejectCoercionAndUnsupportedValues() throws Exception {
        var host=registered(username());String code=create(host),path="/rooms/"+code;
        for(int value=0;value<=3;value++)assertThat(host.patch(path+"/settings",Map.of("retryAllowance",value)).body().get("retryAllowance").asInt()).isEqualTo(value);
        for(Object value:List.of(-1,4,10,1.5,"2"))assertThat(host.patch(path+"/settings",Map.of("retryAllowance",value)).status()).isEqualTo(400);
        var created=host.post("/rooms",Map.of("title","No retries","difficulty","any","questionType","any","questionCount",1,"secondsPerQuestion",5,"maxPlayers",2));
        assertThat(created.status()).isEqualTo(201);assertThat(created.body().get("retryAllowance").asInt()).isZero();
        var nullRetry=new LinkedHashMap<String,Object>(Map.of("title","Invalid retry","difficulty","any","questionType","any","questionCount",1,"secondsPerQuestion",5,"maxPlayers",2));nullRetry.put("retryAllowance",null);assertThat(host.post("/rooms",nullRetry).status()).isEqualTo(400);
    }
    @Test void historicalUnsupportedRoomsKeepTheirOriginalBalancesAndScores() throws Exception {
        var host=registered(username());var player=registered(username());var created=host.post("/rooms",Map.of("title","Historical compatibility","difficulty","any","questionType","TRUE_FALSE","questionCount",1,"secondsPerQuestion",5,"maxPlayers",2,"retryAllowance",0));
        String code=legacyRoom(created.body().get("code").asText()),path="/rooms/"+code;
        jdbc.update("UPDATE quiz_rooms SET scoring_version='retry-v1',retry_allowance=10 WHERE code=?",code);jdbc.update("UPDATE room_questions SET points=5 WHERE room_id=(SELECT id FROM quiz_rooms WHERE code=?)",code);join(player,code);assertThat(player.get(path).body().get("retryAllowance").asInt()).isEqualTo(10);host.post(path+"/start",null);
        var question=player.get(path).body().get("question");player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",correctIndex(code,question)));host.post(path+"/end",null);
        var game=player.get("/analytics/export").body().get("games").get(0);assertThat(game.get("scoringVersion").asText()).isEqualTo("retry-v1");assertThat(game.get("pointsEarned").asInt()).isEqualTo(5);assertThat(game.get("maxPossiblePoints").asInt()).isEqualTo(5);
        assertThat(player.get(path).body().get("retriesRemaining").asInt()).isEqualTo(10);
    }
    @Test void exactTwentyOnePointGameUsesQuestionRetriesRatherThanGameTotal() throws Exception {
        var host=registered(username());var a=registered(username());var b=registered(username());String code=create(host,3,3),path="/rooms/"+code;
        join(a,code);join(b,code);host.post(path+"/start",null);
        for(int number=1;number<=3;number++){
            var view=a.get(path).body();var question=view.get("question");int index=correctIndex(code,question),wrong=(index+1)%question.get("choices").size();var deadline=view.get("deadline");
            for(int retry=0;retry<number-1;retry++){
                var result=a.post(path+"/answers",Map.of("questionNumber",number,"selectedIndex",wrong));assertThat(result.status()).isEqualTo(202);
                assertThat(result.body().get("pointsAvailable").asInt()).isEqualTo(new int[]{7,4}[retry]);
                assertThat(a.get(path).body().get("deadline")).isEqualTo(deadline);clock.advance(Duration.ofMillis(350));
            }
            assertThat(a.post(path+"/answers",Map.of("questionNumber",number,"selectedIndex",index)).body().get("awardedPoints").asInt()).isEqualTo(new int[]{10,7,4}[number-1]);
            assertThat(b.get(path).body().get("retriesRemaining").asInt()).isEqualTo(3);
            if(number<3)b.post(path+"/answers",Map.of("questionNumber",number,"selectedIndex",index));
            else for(int retry=0;retry<4;retry++){b.post(path+"/answers",Map.of("questionNumber",number,"selectedIndex",wrong));clock.advance(Duration.ofMillis(350));}
            clock.advance(Duration.ofSeconds(5));rooms.tick(code);host.post(path+"/next",null);
        }
        var rows=a.get(path).body().get("leaderboard");assertThat(rows.get(0).get("id").asText()).isEqualTo(a.get("/me").body().get("id").asText());assertThat(rows.get(0).get("points").asInt()).isEqualTo(21);assertThat(rows.get(1).get("points").asInt()).isEqualTo(20);
        var game=a.get("/analytics/export").body().get("games").get(0);assertThat(game.get("scoringVersion").asText()).isEqualTo("retry-v2");assertThat(game.get("maxPossiblePoints").asInt()).isEqualTo(30);assertThat(game.get("pointsEarned").asInt()).isEqualTo(21);
        assertThat(a.get(path).body().get("retriesRemaining").asInt()).isZero();
    }
    @Test void threeRetriesGrantTheFourthAttemptAndTwoPointsForEitherType() throws Exception {
        for(String type:List.of("MULTIPLE_CHOICE","TRUE_FALSE")){
            var host=registered(username());var player=registered(username());var created=host.post("/rooms",Map.of("title","Fourth attempt","questionType",type,"difficulty","any","questionCount",1,"secondsPerQuestion",5,"maxPlayers",2,"retryAllowance",3));
            String path="/rooms/"+legacyRoom(created.body().get("code").asText());join(player,created.body().get("code").asText());host.post(path+"/start",null);var question=player.get(path).body().get("question");int index=correctIndex(created.body().get("code").asText(),question);
            for(int i=0;i<3;i++){assertThat(player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",(index+1)%question.get("choices").size())).body().get("outcome").asText()).isEqualTo("RETRY");clock.advance(Duration.ofMillis(350));}
            var answer=Map.of("questionNumber",1,"selectedIndex",index,"submissionId",UUID.randomUUID());var result=player.post(path+"/answers",answer);assertThat(result.body().get("awardedPoints").asInt()).isEqualTo(2);assertThat(player.post(path+"/answers",answer).body()).isEqualTo(result.body());host.post(path+"/end",null);
            assertThat(player.get(path).body().get("leaderboard").get(0).get("points").asInt()).isEqualTo(2);
        }
    }
}
