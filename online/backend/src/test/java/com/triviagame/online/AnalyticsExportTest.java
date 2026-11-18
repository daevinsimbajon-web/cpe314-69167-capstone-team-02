package com.triviagame.online;

import com.triviagame.online.rooms.RoomService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class AnalyticsExportTest extends RoomTestSupport {
    @Autowired RoomService rooms;
    @Test void exportPreservesAttemptsTimeoutsPointsAndPrivacyWithoutDoubleCounting() throws Exception {
        var host=registered(username());var player=registered(username());var outsider=registered(username());String code=create(host),path="/rooms/"+code;
        join(player,code);host.post(path+"/start",null);var q=player.get(path).body().get("question");int index=correctIndex(code,q);
        player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",(index+1)%q.get("choices").size()));
        assertThat(player.get("/analytics/report").body().get("games").isEmpty()).isTrue();
        clock.advance(Duration.ofMillis(400));player.post(path+"/answers",Map.of("questionNumber",1,"selectedIndex",index));
        clock.advance(Duration.ofSeconds(5));rooms.tick(code);host.post(path+"/next",null);
        var result=player.get("/analytics/export");var data=result.body();
        assertThat(result.status()).isEqualTo(200);assertThat(result.headers().firstValue("Content-Disposition").orElse("")).contains("attachment","hootly-my-stats-");
        assertThat(data.get("format").asText()).isEqualTo("hootly.analytics");assertThat(data.get("schemaVersion").asInt()).isEqualTo(1);
        var game=data.get("games").get(0);var answer=game.get("questions").get(0);
        assertThat(game.get("questions").size()).isEqualTo(1);assertThat(game.get("questionsComplete").asBoolean()).isTrue();assertThat(game.get("won").asBoolean()).isTrue();
        assertThat(answer.get("firstTryCorrect").asBoolean()).isFalse();assertThat(answer.get("finalCorrect").asBoolean()).isTrue();
        assertThat(answer.get("attemptCount").asInt()).isEqualTo(2);assertThat(answer.get("retriesUsed").asInt()).isEqualTo(1);assertThat(answer.get("elapsedMs").asInt()).isEqualTo(400);
        assertThat(game.get("pointsEarned")).isEqualTo(answer.get("pointsEarned"));assertThat(game.get("maxPossiblePoints")).isEqualTo(answer.get("maxPossiblePoints"));
        assertThat(player.get("/analytics/report").body().get("player").get("exportPlayerId")).isEqualTo(data.get("player").get("exportPlayerId"));
        assertThat(outsider.get("/analytics/export").body().get("games").isEmpty()).isTrue();
        assertThat(new Browser().get("/analytics/export").status()).isEqualTo(401);
        assertThat(data.toString()).doesNotContain("password","email","correctAnswer","correct_answer","selectedIndex","cookie","token");
        var timeout=host.get("/analytics/report").body().get("games").get(0).get("questions").get(0);
        assertThat(timeout.get("timedOut").asBoolean()).isTrue();assertThat(timeout.get("firstTryCorrect").isNull()).isTrue();assertThat(timeout.get("retriesUsed").asInt()).isZero();
    }
    @Test void inclusiveDatesUseCompletionTimestampAndRequestedTimezone() throws Exception {
        var host=registered(username());var player=registered(username());String code=create(host);join(player,code);host.post("/rooms/"+code+"/start",null);host.post("/rooms/"+code+"/end",null);
        jdbc.update("UPDATE game_sessions SET finished_at=? WHERE room_id=(SELECT id FROM quiz_rooms WHERE code=?)",OffsetDateTime.parse("2026-01-01T23:59:59.999Z"),code);
        assertThat(player.get("/analytics/report?from=2026-01-01&to=2026-01-01&zone=UTC").body().get("games").size()).isEqualTo(1);
        assertThat(player.get("/analytics/report?from=2026-01-02&to=2026-01-02&zone=UTC").body().get("games").isEmpty()).isTrue();
        assertThat(player.get("/analytics/report?from=2026-01-02&to=2026-01-02&zone=Asia%2FManila").body().get("games").size()).isEqualTo(1);
        assertThat(player.get("/analytics/report?from=2026-02-01&to=2026-01-01").status()).isEqualTo(400);
        assertThat(player.get("/analytics/report?zone=invalid-zone").status()).isEqualTo(400);
    }
    @Test void historicalUnknownAttemptAndTimingFieldsStayUnavailable() throws Exception {
        var host=registered(username());var player=registered(username());String code=create(host);join(player,code);host.post("/rooms/"+code+"/start",null);
        var q=player.get("/rooms/"+code).body().get("question");player.post("/rooms/"+code+"/answers",Map.of("questionNumber",1,"selectedIndex",correctIndex(code,q)));host.post("/rooms/"+code+"/end",null);
        jdbc.update("UPDATE quiz_rooms SET scoring_version='legacy-v1' WHERE code=?",code);
        jdbc.update("UPDATE answers SET first_try_correct=NULL,attempt_count=NULL,retries_used=NULL,elapsed_ms=NULL WHERE user_id=(SELECT user_id FROM scores s JOIN game_sessions g ON g.id=s.session_id JOIN quiz_rooms r ON r.id=g.room_id WHERE r.code=? AND s.points>0)",code);
        var exported=player.get("/analytics/report").body().get("games").get(0);var question=exported.get("questions").get(0);
        assertThat(exported.get("scoringVersion").asText()).isEqualTo("legacy-v1");assertThat(question.get("firstTryCorrect").isNull()).isTrue();assertThat(question.get("elapsedMs").isNull()).isTrue();
    }
}
