package com.triviagame.online.analytics;

import com.triviagame.online.auth.AvatarConfig;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Repository
public class AnalyticsRepository {
    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalyticsExport read(UUID user, Instant from, Instant until, Instant now) {
        String name =
                jdbc.queryForObject(
                        "SELECT display_name FROM users WHERE id=? AND deleted_at IS NULL",
                        String.class,
                        user);
        String username =
                jdbc.queryForObject(
                        "SELECT username FROM users WHERE id=? AND deleted_at IS NULL",
                        String.class,
                        user);
        String avatar =
                jdbc.queryForObject(
                        "SELECT avatar_config FROM users WHERE id=?", String.class, user);
        var rows =
                jdbc.queryForList(
                        """
                        SELECT g.id,g.finished_at,s.points,r.scoring_version,
                          CASE WHEN NOT s.forfeited AND s.points=(SELECT MAX(t.points) FROM scores t WHERE t.session_id=g.id AND NOT t.forfeited) THEN TRUE ELSE FALSE END AS won,
                          (SELECT COALESCE(SUM(q.points),0) FROM room_questions q WHERE q.room_id=r.id AND ((r.independent_flow=FALSE AND q.ordinal<=r.current_question) OR (r.independent_flow=TRUE AND EXISTS(SELECT 1 FROM room_question_presentations p WHERE p.room_question_id=q.id AND p.user_id=s.user_id)))
                            AND (NOT s.forfeited OR EXISTS(SELECT 1 FROM answers a WHERE a.room_question_id=q.id AND a.user_id=s.user_id))) AS maximum,
                          (SELECT COUNT(*) FROM room_questions q WHERE q.room_id=r.id AND ((r.independent_flow=FALSE AND q.ordinal<=r.current_question) OR (r.independent_flow=TRUE AND EXISTS(SELECT 1 FROM room_question_presentations p WHERE p.room_question_id=q.id AND p.user_id=s.user_id)))
                            AND (NOT s.forfeited OR EXISTS(SELECT 1 FROM answers a WHERE a.room_question_id=q.id AND a.user_id=s.user_id))) AS presented
                        FROM scores s JOIN game_sessions g ON g.id=s.session_id JOIN quiz_rooms r ON r.id=g.room_id
                        WHERE s.user_id=? AND g.finished_at IS NOT NULL AND g.finished_at>=? AND g.finished_at<?
                        ORDER BY g.finished_at,g.id LIMIT 5001
                        """,
                        user,
                        offset(from),
                        offset(until));
        if (rows.size() > 5000)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select a smaller date range (maximum 5,000 games per export)");
        var questions =
                jdbc.query(
                        """
                        SELECT g.id AS game_id,rq.id,COALESCE(rq.question_id,rq.id) AS source_question_id,rq.text,COALESCE(rq.category_name,c.name) AS name,rq.difficulty,rq.question_type,
                          a.first_try_correct,a.is_correct,a.attempt_count,a.retries_used,a.elapsed_ms,a.points,rq.points AS maximum,a.timed_out
                        FROM answers a JOIN room_questions rq ON rq.id=a.room_question_id
                        LEFT JOIN questions q ON q.id=rq.question_id LEFT JOIN categories c ON c.id=q.category_id
                        JOIN game_sessions g ON g.room_id=rq.room_id
                        WHERE a.user_id=? AND g.finished_at IS NOT NULL AND g.finished_at>=? AND g.finished_at<?
                        ORDER BY g.finished_at,g.id,rq.ordinal
                        """,
                        (rs, n) ->
                                Map.entry(
                                        rs.getObject("game_id", UUID.class),
                                        new AnalyticsExport.Question(
                                                rs.getObject("id").toString(),
                                                rs.getObject("source_question_id").toString(),
                                                rs.getString("text"),
                                                rs.getString("name"),
                                                rs.getString("difficulty"),
                                                rs.getString("question_type"),
                                                rs.getObject("first_try_correct", Boolean.class),
                                                rs.getBoolean("is_correct"),
                                                rs.getObject("attempt_count", Integer.class),
                                                rs.getObject("retries_used", Integer.class),
                                                rs.getObject("elapsed_ms", Long.class),
                                                rs.getInt("points"),
                                                rs.getInt("maximum"),
                                                rs.getBoolean("timed_out"))),
                        user,
                        offset(from),
                        offset(until));
        Map<UUID, List<AnalyticsExport.Question>> grouped = new HashMap<>();
        for (var question : questions)
            grouped.computeIfAbsent(question.getKey(), k -> new ArrayList<>())
                    .add(question.getValue());
        var games =
                rows.stream()
                        .map(row -> {
                            var qs = grouped.getOrDefault((UUID) row.get("id"), List.of());
                            int presented = ((Number) row.get("presented")).intValue();
                            return new AnalyticsExport.Game(
                                    row.get("id").toString(),
                                    com.triviagame.online.DatabaseTimes.instant(
                                            row.get("finished_at")),
                                    ((Number) row.get("points")).intValue(),
                                    ((Number) row.get("maximum")).intValue(),
                                    Boolean.TRUE.equals(row.get("won")),
                                    (String) row.get("scoring_version"),
                                    presented,
                                    qs.size() == presented,
                                    qs);
                        })
                        .toList();
        String pseudonym =
                UUID.nameUUIDFromBytes(
                                ("hootly.analytics:" + user).getBytes(StandardCharsets.UTF_8))
                        .toString();
        return new AnalyticsExport(
                "hootly.analytics",
                1,
                now,
                new AnalyticsExport.Player(
                        pseudonym, name, AvatarConfig.read(avatar, user), username),
                games);
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public Map<String, Object> statistics(UUID user) {
        Map<String, Object> stats =
                jdbc.queryForMap(
                        """
                        SELECT COUNT(*) AS "gamesPlayed", COALESCE(SUM(s.points),0) AS "totalPoints",
                          COALESCE(SUM(CASE WHEN NOT s.forfeited AND s.points = (SELECT MAX(t.points) FROM scores t
                            WHERE t.session_id=s.session_id AND NOT t.forfeited) THEN 1 ELSE 0 END),0) AS "totalWins"
                        FROM scores s JOIN game_sessions g ON g.id=s.session_id
                        WHERE s.user_id=? AND g.finished_at IS NOT NULL
                        """,
                        user);
        Double accuracy =
                jdbc.queryForObject(
                        """
                        SELECT 100.0 * SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0)
                        FROM answers a JOIN room_questions q ON q.id=a.room_question_id
                        JOIN game_sessions g ON g.room_id=q.room_id
                        WHERE a.user_id=? AND g.finished_at IS NOT NULL
                        """,
                        Double.class,
                        user);
        stats.put("averageAccuracy", accuracy);
        return stats;
    }

    public List<Map<String, Object>> history(UUID user) {
        return jdbc.queryForList(
                """
                SELECT r.code AS "roomCode",r.title AS "title",g.started_at AS "startedAt",
                  g.finished_at AS "finishedAt",s.points AS "points",s.forfeited AS "forfeited",
                  CASE WHEN r.host_id=? THEN TRUE ELSE FALSE END AS "hosted",
                  CASE WHEN s.user_id IS NULL THEN NULL WHEN s.forfeited THEN 0 ELSE 1+(SELECT COUNT(DISTINCT t.points) FROM scores t
                    WHERE t.session_id=s.session_id AND NOT t.forfeited AND t.points>s.points) END AS "rank"
                FROM game_sessions g JOIN quiz_rooms r ON r.id=g.room_id
                LEFT JOIN scores s ON s.session_id=g.id AND s.user_id=?
                WHERE (r.host_id=? OR s.user_id IS NOT NULL) AND g.finished_at IS NOT NULL
                ORDER BY g.finished_at DESC LIMIT 100
                """,
                user,
                user,
                user);
    }

    public List<Map<String, Object>> categoryAccuracy(UUID user) {
        return jdbc.queryForList(
                """
                SELECT COALESCE(rq.category_name,c.name) AS "category",COUNT(a.id) AS "answered",
                  SUM(CASE WHEN a.is_correct THEN 1 ELSE 0 END) AS "correct"
                FROM answers a JOIN room_questions rq ON rq.id=a.room_question_id
                LEFT JOIN questions q ON q.id=rq.question_id LEFT JOIN categories c ON c.id=q.category_id
                JOIN game_sessions g ON g.room_id=rq.room_id WHERE a.user_id=? AND g.finished_at IS NOT NULL
                GROUP BY COALESCE(rq.category_name,c.name) ORDER BY COALESCE(rq.category_name,c.name)
                """,
                user);
    }
}
