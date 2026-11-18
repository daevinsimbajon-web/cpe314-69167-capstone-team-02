package com.triviagame.online.rooms;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Parameterized persistence operations for room participation, snapshots, attempts and scores. */
@Repository
public class RoomDataRepository {
    private final JdbcTemplate jdbc;

    public RoomDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int activeRooms(UUID user, Instant now) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM quiz_rooms WHERE host_id=?
                AND status NOT IN ('FINISHED','CLOSED') AND expires_at>?
                """,
                Integer.class,
                user,
                time(now));
    }

    public boolean categoryExists(Long category) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM categories WHERE id=?", Integer.class, category)
                > 0;
    }

    public void snapshot(
            UUID room,
            UUID question,
            int ordinal,
            String text,
            String correct,
            String choices,
            String difficulty,
            String type,
            int points,
            String category) {
        jdbc.update(
                """
                INSERT INTO room_questions(id,room_id,question_id,ordinal,text,correct_answer,
                choices,difficulty,question_type,points,category_name) VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                room,
                question,
                ordinal,
                text,
                correct,
                choices,
                difficulty,
                type,
                points,
                category);
    }

    public void participant(UUID room, UUID user, String role, Instant now) {
        jdbc.update(
                "INSERT INTO room_participants(room_id,user_id,role,joined_at) VALUES(?,?,?,?)",
                room,
                user,
                role,
                time(now));
    }

    public int participantCount(UUID room) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM room_participants WHERE room_id=? AND left_at IS NULL
                """,
                Integer.class,
                room);
    }

    public boolean rejoin(UUID room, UUID user, Instant now) {
        return jdbc.update(
                        """
                        UPDATE room_participants SET left_at=NULL,removed_by_host=FALSE,joined_at=? WHERE room_id=? AND user_id=?
                        """,
                        time(now),
                        room,
                        user)
                > 0;
    }

    public boolean hasHistoricalScore(UUID room, UUID user) {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM scores s JOIN game_sessions g ON g.id=s.session_id
                        WHERE g.room_id=? AND s.user_id=?
                        """,
                        Integer.class,
                        room,
                        user)
                > 0;
    }

    public List<Map<String, Object>> roomsFor(UUID user) {
        return jdbc.queryForList(
                """
                SELECT r.code,r.title,r.status,r.created_at,r.host_id FROM quiz_rooms r
                JOIN room_participants p ON p.room_id=r.id WHERE p.user_id=? AND p.left_at IS NULL
                ORDER BY r.created_at DESC LIMIT 50
                """,
                user);
    }

    public int questionCount(UUID room) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_questions WHERE room_id=?", Integer.class, room);
    }

    public void start(UUID session, UUID room, int retryAllowance, Instant now) {
        jdbc.update(
                "INSERT INTO game_sessions(id,room_id,started_at) VALUES(?,?,?)",
                session,
                room,
                time(now));
        jdbc.update(
                """
                INSERT INTO scores(session_id,user_id,retries_remaining)
                SELECT ?,user_id,? FROM room_participants WHERE room_id=? AND left_at IS NULL
                """,
                session,
                retryAllowance,
                room);
    }

    public List<Map<String, Object>> replay(UUID user, UUID submission) {
        return jdbc.queryForList(
                """
                SELECT a.*,q.ordinal,q.room_id,q.points AS question_maximum FROM answer_attempts a
                JOIN room_questions q ON q.id=a.room_question_id WHERE a.user_id=? AND a.submission_id=?
                """,
                user,
                submission);
    }

    public boolean finalized(UUID question, UUID user) {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM answers WHERE room_question_id=? AND user_id=?
                        """,
                        Integer.class,
                        question,
                        user)
                > 0;
    }

    public List<Map<String, Object>> attempts(UUID question, UUID user) {
        return jdbc.queryForList(
                """
                SELECT * FROM answer_attempts WHERE room_question_id=? AND user_id=?
                ORDER BY attempt_number
                """,
                question,
                user);
    }

    public List<Integer> balances(UUID room, UUID user) {
        return jdbc.queryForList(
                """
                SELECT s.retries_remaining FROM scores s JOIN game_sessions g ON g.id=s.session_id
                WHERE g.room_id=? AND s.user_id=?
                """,
                Integer.class,
                room,
                user);
    }

    public void balance(UUID room, UUID user, int remaining) {
        jdbc.update(
                """
                UPDATE scores SET retries_remaining=? WHERE user_id=?
                AND session_id=(SELECT id FROM game_sessions WHERE room_id=?)
                """,
                remaining,
                user,
                room);
    }

    public void attempt(
            UUID question,
            UUID user,
            UUID submission,
            int number,
            int selected,
            boolean correct,
            String outcome,
            int remaining,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO answer_attempts(id,room_question_id,user_id,submission_id,attempt_number,
                selected_index,is_correct,outcome,retries_remaining,accepted_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                question,
                user,
                submission,
                number,
                selected,
                correct,
                outcome,
                remaining,
                time(now));
    }

    public void answer(
            UUID question,
            UUID user,
            Integer selected,
            boolean correct,
            int points,
            Instant now,
            boolean timedOut,
            Boolean firstCorrect,
            int attempts,
            int retries,
            long elapsed) {
        jdbc.update(
                """
                INSERT INTO answers(id,room_question_id,user_id,selected_index,is_correct,points,
                answered_at,timed_out,first_try_correct,attempt_count,retries_used,elapsed_ms)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                question,
                user,
                selected,
                correct,
                points,
                time(now),
                timedOut,
                firstCorrect,
                attempts,
                retries,
                elapsed);
    }

    public void forfeit(UUID room, UUID user) {
        jdbc.update(
                """
                UPDATE scores SET forfeited=TRUE WHERE user_id=?
                AND session_id=(SELECT id FROM game_sessions WHERE room_id=?)
                """,
                user,
                room);
    }

    public void leave(UUID room, UUID user, Instant now) {
        jdbc.update(
                """
                UPDATE room_participants SET left_at=?
                WHERE room_id=? AND user_id=? AND left_at IS NULL
                """,
                time(now),
                room,
                user);
    }

    public void remove(UUID room,UUID user,Instant now){
        jdbc.update("UPDATE room_participants SET left_at=?,removed_by_host=TRUE WHERE room_id=? AND user_id=? AND left_at IS NULL",time(now),room,user);
    }

    public boolean removedByHost(UUID room,UUID user){
        return jdbc.queryForObject("SELECT COUNT(*) FROM room_participants WHERE room_id=? AND user_id=? AND left_at IS NOT NULL AND removed_by_host=TRUE",Integer.class,room,user)>0;
    }

    public List<String> relatedRooms(UUID user) {
        return jdbc.queryForList(
                """
                SELECT DISTINCT r.code FROM quiz_rooms r JOIN room_participants p ON p.room_id=r.id
                WHERE r.host_id=? OR p.user_id=? ORDER BY r.code
                """,
                String.class,
                user,
                user);
    }

    public boolean lockActiveAccount(UUID user) {
        var rows =
                jdbc.query(
                        "SELECT deleted_at FROM users WHERE id=? FOR UPDATE",
                        (rs, row) -> rs.getObject(1) == null,
                        user);
        return !rows.isEmpty() && rows.get(0);
    }

    public UUID session(UUID room) {
        return jdbc.queryForObject(
                "SELECT id FROM game_sessions WHERE room_id=?", UUID.class, room);
    }

    public List<UUID> eligiblePlayers(UUID session) {
        return jdbc.queryForList(
                "SELECT user_id FROM scores WHERE session_id=? AND forfeited=FALSE",
                UUID.class,
                session);
    }

    public List<Map<String, Object>> roundAnswers(UUID room, int ordinal) {
        return jdbc.queryForList(
                """
                SELECT a.user_id,a.points,a.is_correct FROM answers a
                JOIN room_questions q ON q.id=a.room_question_id WHERE q.room_id=? AND q.ordinal=?
                """,
                room,
                ordinal);
    }

    public void addScore(UUID session, UUID user, int points, boolean correct) {
        jdbc.update(
                """
                UPDATE scores SET points=points+?,correct_count=correct_count+?
                WHERE session_id=? AND user_id=?
                """,
                points,
                correct ? 1 : 0,
                session,
                user);
    }

    public void presented(UUID room, int ordinal, Instant now) {
        jdbc.update(
                "UPDATE room_questions SET presented_at=? WHERE room_id=? AND ordinal=?",
                time(now),
                room,
                ordinal);
    }

    public void finish(UUID room, Instant now) {
        jdbc.update("UPDATE game_sessions SET finished_at=? WHERE room_id=?", time(now), room);
    }

    public List<Map<String, Object>> participants(UUID room) {
        return jdbc.queryForList(
                """
                SELECT p.user_id,u.display_name,u.username,u.avatar_config,p.role,p.left_at FROM room_participants p
                JOIN users u ON u.id=p.user_id WHERE p.room_id=? ORDER BY p.joined_at,u.id
                """,
                room);
    }

    public List<Map<String, Object>> rankings(UUID room) {
        return jdbc.queryForList(
                """
                SELECT u.id,u.display_name,u.username,u.avatar_config,s.points,s.correct_count,s.forfeited,
                  CASE WHEN s.forfeited THEN 0 ELSE
                  DENSE_RANK() OVER(ORDER BY s.forfeited ASC,s.points DESC) END AS ranking
                FROM scores s JOIN game_sessions g ON g.id=s.session_id JOIN users u ON u.id=s.user_id
                WHERE g.room_id=? ORDER BY s.forfeited ASC,s.points DESC,u.display_name,u.id
                """,
                room);
    }

    public List<Map<String, Object>> submitted(UUID question, UUID user) {
        return jdbc.queryForList(
                """
                SELECT selected_index,points FROM answers WHERE room_question_id=? AND user_id=?
                """,
                question,
                user);
    }

    public Map<String, Object> current(UUID room, int ordinal) {
        return jdbc.queryForMap(
                "SELECT * FROM room_questions WHERE room_id=? AND ordinal=?", room, ordinal);
    }

    public boolean member(UUID room, UUID user) {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM room_participants
                        WHERE room_id=? AND user_id=? AND left_at IS NULL
                        """,
                        Integer.class,
                        room,
                        user)
                > 0;
    }

    private static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
