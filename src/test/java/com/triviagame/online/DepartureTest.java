package com.triviagame.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.triviagame.online.rooms.RoomService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Map;

class DepartureTest extends RoomTestSupport {
    @Autowired RoomService rooms;

    @Test
    void leavingDoesNotInventTimeoutsOrAwardWinsAndDoesNotRemoveRealSubmittedAnswers()
            throws Exception {
        var host = registered(username());
        var player = registered(username());
        String code = create(host, 3, 3), path = "/rooms/" + code;
        join(player, code);
        host.post(path + "/start", null);
        var question = player.get(path).body().path("question");
        player.post(
                path + "/answers",
                Map.of("questionNumber", 1, "selectedIndex", correctIndex(code, question)));
        assertThat(player.post(path + "/leave", null).status()).isEqualTo(204);
        for (int number = 0; number < 3; number++) {
            clock.advance(Duration.ofSeconds(5));
            rooms.tick(code);
            host.post(path + "/next", null);
        }
        var report = player.get("/analytics/report").body().path("games").get(0);
        assertThat(report.path("questions")).hasSize(1);
        assertThat(report.path("questions").get(0).path("timedOut").asBoolean()).isFalse();
        assertThat(report.path("maxPossiblePoints").asInt()).isEqualTo(10);
        assertThat(report.path("won").asBoolean()).isFalse();
        assertThat(report.path("questionsComplete").asBoolean()).isTrue();
        assertThat(player.get("/me").body().path("statistics").path("totalWins").asInt()).isZero();
        var rows = host.get(path).body().path("leaderboard");
        assertThat(rows.get(rows.size() - 1).path("forfeited").asBoolean()).isTrue();
        assertThat(rows.get(rows.size() - 1).path("rank").asInt()).isZero();
        // Leaving an already completed game does not retroactively disqualify its winner.
        host.post(path + "/leave", null);
        assertThat(host.get("/me").body().path("statistics").path("totalWins").asInt())
                .isEqualTo(1);
    }

    @Test
    void leavingAfterTheDeadlineRetainsTheRealTimeout() throws Exception {
        var host = registered(username());
        var player = registered(username());
        String code = create(host), path = "/rooms/" + code;
        join(player, code);
        host.post(path + "/start", null);
        clock.advance(Duration.ofSeconds(6));
        player.post(path + "/leave", null);
        host.post(path + "/next", null);
        var questions =
                player.get("/analytics/report").body().path("games").get(0).path("questions");
        assertThat(questions).hasSize(1);
        assertThat(questions.get(0).path("timedOut").asBoolean()).isTrue();
    }
}
