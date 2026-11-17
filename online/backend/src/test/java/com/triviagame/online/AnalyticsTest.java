package com.triviagame.online;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnalyticsTest extends RoomTestSupport {
    @Test void finishedStatisticsArePrivateAndExcludeUnfinishedGames() throws Exception {
        Browser host=registered(username()),player=registered(username()),outsider=registered(username());
        String code=create(host),path="/rooms/"+code;
        assertThat(join(player,code).status()).isEqualTo(200);
        assertThat(host.post(path+"/start",null).status()).isEqualTo(200);
        var question=player.get(path).body().get("question");
        int expected=10;
        assertThat(player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",correctIndex(code,question))).status()).isEqualTo(202);
        assertThat(player.get("/me").body().get("statistics").get("totalPoints").asInt()).isZero();
        assertThat(player.get("/me").body().get("statistics").get("averageAccuracy").isNull()).isTrue();
        assertThat(player.get("/history").body().isEmpty()).isTrue();
        assertThat(player.get("/analytics").body().isEmpty()).isTrue();
        assertThat(host.post(path+"/end",null).status()).isEqualTo(200);
        var stats=player.get("/me").body().get("statistics");
        assertThat(stats.get("gamesPlayed").asInt()).isEqualTo(1);
        assertThat(stats.get("totalPoints").asInt()).isEqualTo(expected);
        assertThat(stats.get("totalWins").asInt()).isEqualTo(1);
        assertThat(stats.get("averageAccuracy").asDouble()).isEqualTo(100.0);
        assertThat(host.get("/me").body().get("statistics").get("totalWins").asInt()).isZero();
        var accuracy=player.get("/analytics").body();
        assertThat(accuracy.size()).isEqualTo(1);
        assertThat(accuracy.get(0).get("answered").asInt()).isEqualTo(1);
        assertThat(accuracy.get(0).get("correct").asInt()).isEqualTo(1);
        assertThat(outsider.get("/me").body().get("statistics").get("gamesPlayed").asInt()).isZero();
        assertThat(outsider.get("/history").body().isEmpty()).isTrue();
        assertThat(outsider.get("/analytics").body().isEmpty()).isTrue();
    }

    @Test void tiedWinnersShareFirstPlaceAndRetainIndividualHistory() throws Exception {
        Browser host=registered(username()),player=registered(username());
        String code=create(host),path="/rooms/"+code;
        join(player,code);host.post(path+"/start",null);
        var question=player.get(path).body().get("question");
        var answer=Map.of("questionNumber",1,"selectedIndex",correctIndex(code,question));
        assertThat(host.post(path+"/answers",answer).status()).isEqualTo(202);
        assertThat(player.post(path+"/answers",answer).status()).isEqualTo(202);
        assertThat(host.post(path+"/end",null).status()).isEqualTo(200);
        for (Browser browser:java.util.List.of(host,player)) {
            assertThat(browser.get("/me").body().get("statistics").get("totalWins").asInt()).isEqualTo(1);
            assertThat(browser.get("/history").body()).hasSize(1);
            assertThat(browser.get("/history").body().get(0).get("rank").asInt()).isEqualTo(1);
        }
        assertThat(host.get("/history").body().get(0).get("hosted").asBoolean()).isTrue();
    }

    @Test void zeroPointTieRemainsAWinInReportAndHistory() throws Exception {
        Browser host=registered(username()),player=registered(username());
        String code=create(host),path="/rooms/"+code;
        join(player,code);host.post(path+"/start",null);
        var question=player.get(path).body().get("question");
        int wrong=(correctIndex(code,question)+1)%question.get("choices").size();
        for(Browser browser:java.util.List.of(host,player))browser.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",wrong));
        assertThat(host.post(path+"/end",null).status()).isEqualTo(200);
        for(Browser browser:java.util.List.of(host,player)) {
            var game=browser.get("/analytics/report").body().get("games").get(0);
            assertThat(game.get("pointsEarned").asInt()).isZero();
            assertThat(game.get("won").asBoolean()).isTrue();
            assertThat(browser.get("/history").body().get(0).get("rank").asInt()).isEqualTo(1);
        }
    }
}
