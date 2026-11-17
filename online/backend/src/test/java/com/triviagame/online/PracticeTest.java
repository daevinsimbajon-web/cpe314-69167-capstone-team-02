package com.triviagame.online;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PracticeTest extends HttpTestSupport {
    @Autowired JdbcTemplate jdbc;

    private Map<String, Object> question() {
        return Map.of(
                "type",
                "TRUE_FALSE",
                "text",
                "The Sun is a star.",
                "options",
                List.of("True", "False"),
                "correctIndex",
                0);
    }

    private String quiz(Browser user, String category) throws Exception {
        var input =
                new java.util.LinkedHashMap<String, Object>(
                        Map.of(
                                "title",
                                "Practice quiz",
                                "status",
                                "READY",
                                "version",
                                0,
                                "questions",
                                List.of(question(), question())));
        if (category != null) {
            input.put("categoryId", category);
        }
        var result = user.post("/quizzes", input);
        assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
        return result.body().path("quiz").path("id").asText();
    }

    @Test
    void practiceIsPrivatePersistentIdempotentAndNeverChangesCompetitiveStatistics()
            throws Exception {
        var owner = registered(username());
        var outsider = registered(username());
        String quiz = quiz(owner, null);
        int scores = jdbc.queryForObject("SELECT count(*) FROM scores", Integer.class);
        var initial = owner.post("/quizzes/" + quiz + "/practice", null);
        assertThat(initial.status()).isEqualTo(200);
        String path = "/practice/" + initial.body().path("id").asText();
        assertThat(initial.body().has("correctIndex")).isFalse();
        assertThat(initial.body().path("feedback").isNull()).isTrue();
        assertThat(outsider.get(path).status()).isEqualTo(404);
        assertThat(outsider.post("/quizzes/" + quiz + "/practice", null).status()).isIn(403, 404);
        assertThat(
                        owner.post(
                                        path + "/answers",
                                        Map.of("version", 0, "actionId", UUID.randomUUID()))
                                .status())
                .isEqualTo(400);
        assertThat(
                        owner.post(
                                        path + "/answers",
                                        Map.of(
                                                "selectedIndex",
                                                0.5,
                                                "version",
                                                0,
                                                "actionId",
                                                UUID.randomUUID()))
                                .status())
                .isEqualTo(400);
        assertThat(
                        owner.post(
                                        path + "/undo",
                                        Map.of("version", 0, "actionId", UUID.randomUUID()))
                                .status())
                .isEqualTo(400);
        var answer = Map.of("selectedIndex", 0, "version", 0, "actionId", UUID.randomUUID());
        var scored = owner.post(path + "/answers", answer);
        assertThat(scored.status()).as(scored.body().toString()).isEqualTo(200);
        assertThat(scored.body().path("score").asInt()).isEqualTo(5);
        assertThat(owner.post(path + "/answers", answer).body()).isEqualTo(scored.body());
        assertThat(owner.get(path).body()).isEqualTo(scored.body());
        assertThat(
                        owner.post(
                                        path + "/answers",
                                        Map.of(
                                                "selectedIndex",
                                                0,
                                                "version",
                                                0,
                                                "actionId",
                                                UUID.randomUUID()))
                                .status())
                .isEqualTo(409);
        var undo = Map.of("version", 1, "actionId", UUID.randomUUID());
        var restored = owner.post(path + "/undo", undo);
        assertThat(restored.body().path("score").asInt()).isZero();
        assertThat(restored.body().path("answered").asInt()).isZero();
        assertThat(restored.body().path("question").asText()).isEqualTo("The Sun is a star.");
        assertThat(owner.post(path + "/undo", undo).body()).isEqualTo(restored.body());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scores", Integer.class))
                .isEqualTo(scores);
        assertThat(owner.get("/analytics/report").body().path("games")).isEmpty();
        clock.advance(Duration.ofHours(7));
        assertThat(owner.get(path).status()).isEqualTo(404);
    }

    @Test
    void categorySearchRecursesThroughMultipleLevelsWithoutReadingAnotherOwnersQuestions()
            throws Exception {
        var owner = registered(username());
        var outsider = registered(username());
        String root =
                owner.post("/quizzes/categories", Map.of("name", "Science"))
                        .body()
                        .path("id")
                        .asText();
        String child =
                owner.post("/quizzes/categories", Map.of("name", "Physics", "parentId", root))
                        .body()
                        .path("id")
                        .asText();
        String leaf =
                owner.post("/quizzes/categories", Map.of("name", "Astrophysics", "parentId", child))
                        .body()
                        .path("id")
                        .asText();
        quiz(owner, leaf);
        quiz(outsider, null);
        assertThat(owner.get("/quizzes/library?categoryId=" + root).body()).hasSize(1);
        assertThat(owner.get("/quizzes/library?categoryId=" + child).body()).hasSize(1);
        assertThat(owner.get("/quizzes/library?categoryId=" + leaf).body()).hasSize(1);
        assertThat(outsider.get("/quizzes/library?categoryId=" + root).status()).isEqualTo(400);
        assertThat(
                        outsider.post(
                                        "/quizzes/categories",
                                        Map.of("name", "Stolen branch", "parentId", root))
                                .status())
                .isEqualTo(400);
    }
}
