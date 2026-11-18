package com.triviagame.online;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RoomQuestionSourcesTest extends RoomTestSupport {
    @Test
    void requestsWithoutAnActiveSourceCannotCreateRooms() throws Exception {
        var host = registered(username());
        var request = new LinkedHashMap<String, Object>(Map.of(
                "title", "Shared bank request", "difficulty", "any", "questionType", "any",
                "questionCount", 1, "secondsPerQuestion", 20, "maxPlayers", 2));

        assertThat(host.post("/rooms", request).status()).isEqualTo(400);
        request.put("questionSource", "LOCAL");
        assertThat(host.post("/rooms", request).status()).isEqualTo(400);
        request.put("questionSource", "OPENTDB");
        request.put("categoryId", 1);
        assertThat(host.post("/rooms", request).status()).isEqualTo(400);
        assertThat(host.get("/rooms").body()).isEmpty();
    }

    @Test
    void sharedBankRoutesAreGone() throws Exception {
        var user = registered(username());
        assertThat(user.get("/bank/categories").status()).isEqualTo(404);
        assertThat(user.post("/bank/import", Map.of("source", "archive", "json", "[]")).status()).isEqualTo(404);
        assertThat(user.post("/bank/fetch", Map.of("amount", 5)).status()).isEqualTo(404);
    }

    @Test
    void incompleteLegacyLobbyCannotAddQuestionsOrStartASession() throws Exception {
        var host = registered(username());
        var player = registered(username());
        String code = create(host);
        assertThat(join(player, code).status()).isEqualTo(200);
        jdbc.update("UPDATE quiz_rooms SET custom_quiz_id=NULL, question_count=2 WHERE code=?", code);

        assertThat(host.post("/rooms/" + code + "/start", null).status()).isEqualTo(409);
        assertThat(host.get("/rooms/" + code).body().get("status").asText()).isEqualTo("LOBBY");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM game_sessions g JOIN quiz_rooms r ON g.room_id=r.id
                WHERE r.code=?
                """, Integer.class, code)).isZero();
    }

    @Test
    void retainedSharedQuestionSnapshotsStillPlayAndExport() throws Exception {
        var host = registered(username());
        var player = registered(username());
        String code = create(host);
        String path = "/rooms/" + code;
        jdbc.update("UPDATE quiz_rooms SET custom_quiz_id=NULL WHERE code=?", code);
        jdbc.update("""
                UPDATE questions SET owner_user_id=NULL, archived=TRUE WHERE id IN (
                    SELECT rq.question_id FROM room_questions rq JOIN quiz_rooms r ON r.id=rq.room_id
                    WHERE r.code=?)
                """, code);
        jdbc.update("""
                UPDATE room_questions SET category_name=NULL
                WHERE room_id=(SELECT id FROM quiz_rooms WHERE code=?)
                """, code);
        assertThat(join(player, code).status()).isEqualTo(200);
        assertThat(host.post(path + "/start", null).status()).isEqualTo(200);
        var question = player.get(path).body().get("question");
        assertThat(player.post(path + "/answers", Map.of(
                "questionNumber", 1, "selectedIndex", correctIndex(code, question))).status()).isEqualTo(202);
        assertThat(host.post(path + "/end", null).status()).isEqualTo(200);
        assertThat(player.get("/history").body().get(0).get("roomCode").asText()).isEqualTo(code);
        var exported = player.get("/analytics/export").body().get("games").get(0).get("questions").get(0);
        assertThat(exported.get("category").asText()).isEqualTo("General Knowledge");
        assertThat(exported.get("pointsEarned").asInt()).isEqualTo(10);
    }
}
