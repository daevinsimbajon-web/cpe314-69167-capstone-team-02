package com.triviagame.online.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SnapshotValidationTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SnapshotValidation validation = new SnapshotValidation(json);

    private ObjectNode snapshot(int attempts, int retries, boolean timeout, boolean correct) throws Exception {
        ObjectNode root=(ObjectNode)json.readTree("""
            {"format":"hootly.analytics","schemaVersion":1,"exportedAt":"2026-01-01T00:00:00Z",
             "player":{"exportPlayerId":"player","displayName":"Player"},
             "games":[{"gameId":"game","playedAt":"2026-01-01T00:00:00Z","pointsEarned":0,
             "maxPossiblePoints":10,"won":false,"scoringVersion":"retry-v2","presentedQuestions":1,"questionsComplete":true,
             "questions":[{"questionId":"q","category":"Science","firstTryCorrect":null,"finalCorrect":false,
             "attemptCount":0,"retriesUsed":0,"pointsEarned":0,"maxPossiblePoints":10,"timedOut":true}]}]}
            """);
        ObjectNode game=(ObjectNode)root.path("games").get(0),q=(ObjectNode)game.path("questions").get(0);
        q.put("attemptCount",attempts);q.put("retriesUsed",retries);q.put("timedOut",timeout);q.put("finalCorrect",correct);
        if(attempts>0)q.put("firstTryCorrect",correct&&attempts==1);
        int points=correct?new int[]{10,7,4,2}[retries]:0;q.put("pointsEarned",points);game.put("pointsEarned",points);
        return root;
    }
    @Test void acceptsActualTimeoutAndRetryRelationships() throws Exception {
        assertThatCode(()->validation.read(snapshot(0,0,true,false))).doesNotThrowAnyException();
        assertThatCode(()->validation.read(snapshot(1,0,true,false))).doesNotThrowAnyException();
        assertThatCode(()->validation.read(snapshot(2,1,true,false))).doesNotThrowAnyException();
        assertThatCode(()->validation.read(snapshot(1,1,true,false))).doesNotThrowAnyException();
        assertThatCode(()->validation.read(snapshot(2,1,false,true))).doesNotThrowAnyException();
        assertThatThrownBy(()->validation.read(snapshot(2,0,true,false))).hasMessageContaining("Retries do not match");
        assertThatThrownBy(()->validation.read(snapshot(1,1,false,false))).hasMessageContaining("Retries do not match");
    }
}
