package com.triviagame.online.practice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

/** Stores private practice snapshots separately from competitive answers and scores. */
@Repository
public class PracticeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public PracticeRepository(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public PracticeSession create(UUID user, UUID quiz, PracticeState state) {
        // A restarted practice replaces only previous private practice sessions for this quiz.
        jdbc.update(
                "DELETE FROM quiz_practice_sessions WHERE owner_id=? AND quiz_id=?", user, quiz);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO quiz_practice_sessions(id,owner_id,quiz_id,state_document,expires_at)
                VALUES(?,?,?,?,?)
                """,
                id,
                user,
                quiz,
                write(state),
                clock.instant().plus(Duration.ofHours(6)).atOffset(ZoneOffset.UTC));
        return new PracticeSession(id, 0, null, state);
    }

    /** Match quiz editing/hosting lock order: account, quiz, then practice state. */
    public boolean lockOwner(UUID user) {
        return !jdbc.queryForList(
                        "SELECT id FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE",
                        UUID.class,
                        user)
                .isEmpty();
    }

    public PracticeSession load(UUID user, UUID id, boolean lock) {
        String sql =
                """
                SELECT id,version,last_action_id,state_document FROM quiz_practice_sessions
                WHERE owner_id=? AND id=? AND expires_at>?
                """
                        + (lock ? " FOR UPDATE" : "");
        return jdbc
                .query(
                        sql,
                        (rs, row) ->
                                new PracticeSession(
                                        rs.getObject("id", UUID.class),
                                        rs.getInt("version"),
                                        rs.getObject("last_action_id", UUID.class),
                                        read(rs.getString("state_document"))),
                        user,
                        id,
                        clock.instant().atOffset(ZoneOffset.UTC))
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Practice expired or is unavailable. Start again from your quiz."));
    }

    public PracticeSession save(PracticeSession previous, PracticeState next, UUID action) {
        jdbc.update(
                """
                UPDATE quiz_practice_sessions SET state_document=?,version=version+1,last_action_id=?
                WHERE id=? AND version=?
                """,
                write(next),
                action,
                previous.id(),
                previous.version());
        return new PracticeSession(previous.id(), previous.version() + 1, action, next);
    }

    private String write(PracticeState state) {
        try {
            return json.writeValueAsString(state);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Practice could not be saved", failure);
        }
    }

    private PracticeState read(String document) {
        try {
            return json.readValue(document, PracticeState.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Practice could not be loaded", failure);
        }
    }
}
