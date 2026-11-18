package com.triviagame.online;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Shared real-HTTP room fixtures; no test owns a second copy of room setup or answer lookup. */
abstract class RoomTestSupport extends HttpTestSupport {
    @Autowired JdbcTemplate jdbc;

    String create(Browser host) throws Exception { return create(host, 1, 3); }

    String create(Browser host, int questions, int capacity) throws Exception {
        Result result = host.post("/rooms", savedRoomRequest(host, questions, capacity, "MULTIPLE_CHOICE"));
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().get("code").asText()).matches("[A-Z2-9]{8}");
        String code=result.body().get("code").asText();
        // Exercise compatibility with rooms persisted before the new independent flow.
        jdbc.update("UPDATE quiz_rooms SET independent_flow=FALSE WHERE code=?",code);
        return code;
    }

    Map<String, Object> savedRoomRequest(Browser host, int count, int capacity, String type)
            throws Exception {
        List<String> options = type.equals("TRUE_FALSE") ? List.of("True", "False") : List.of("Yes", "No");
        var questions = IntStream.rangeClosed(1, count)
                .mapToObj(i -> Map.of("type", type, "text", "Fixture question " + i,
                        "options", options, "correctIndex", 0))
                .toList();
        var quiz = host.post("/quizzes", Map.of("title", "Room fixture quiz", "status", "READY",
                "version", 0, "questions", questions));
        assertThat(quiz.status()).as(quiz.body().toString()).isEqualTo(201);
        return new LinkedHashMap<>(Map.of("title", "Test quiz night", "questionType", "any",
                "questionSource", "LOCAL", "quizId", quiz.body().get("quiz").get("id").asText(),
                "questionCount", count, "secondsPerQuestion", 5, "maxPlayers", capacity,
                "retryAllowance", 3));
    }

    String legacyRoom(String code){jdbc.update("UPDATE quiz_rooms SET independent_flow=FALSE WHERE code=?",code);return code;}

    Result join(Browser player, String code) throws Exception {
        return player.post("/rooms/" + code + "/join", Map.of());
    }

    int correctIndex(String code, JsonNode question) {
        String answer = jdbc.queryForObject("""
                SELECT q.correct_answer FROM room_questions q JOIN quiz_rooms r ON r.id=q.room_id
                WHERE r.code=? AND q.ordinal=?
                """, String.class, code, question.get("number").asInt() - 1);
        for (int i = 0; i < question.get("choices").size(); i++) {
            if (question.get("choices").get(i).asText().equals(answer)) return i;
        }
        throw new AssertionError("Correct answer must occur among stored choices");
    }
}
