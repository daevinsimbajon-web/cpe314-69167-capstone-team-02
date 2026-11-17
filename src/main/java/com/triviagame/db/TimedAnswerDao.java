package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class TimedAnswerDao {

    public record Ranking(
            int playerId,
            String name,
            int points,
            long elapsedMillis,
            int turnOrder) {
    }

    public void initialize() throws SQLException {

        new SessionPlayerDao().initialize();

        try (
            Connection c = Database.getConnection();
            Statement st = c.createStatement()
        ) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS timed_answers (
                    session_id INTEGER NOT NULL
                        REFERENCES game_sessions(id),

                    player_id INTEGER NOT NULL
                        REFERENCES players(id),

                    question_id INTEGER NOT NULL
                        REFERENCES questions(id),

                    elapsed_ms INTEGER NOT NULL
                        CHECK (elapsed_ms >= 0),

                    timed_out INTEGER NOT NULL
                        CHECK (timed_out IN (0,1)),

                    PRIMARY KEY (
                        session_id,
                        player_id,
                        question_id
                    )
                )
                """);
        }
    }

    public void save(
            int sessionId,
            int playerId,
            int questionId,
            long elapsedMs,
            boolean timedOut) throws SQLException {

        try (
            Connection c = Database.getConnection();
            PreparedStatement ps = c.prepareStatement("""
                INSERT INTO timed_answers(
                    session_id,
                    player_id,
                    question_id,
                    elapsed_ms,
                    timed_out
                )
                VALUES (?, ?, ?, ?, ?)

                ON CONFLICT(
                    session_id, player_id, question_id
                )

                DO UPDATE SET
                    elapsed_ms = excluded.elapsed_ms,
                    timed_out = excluded.timed_out
                """)
        ) {
            ps.setInt(1, sessionId);
            ps.setInt(2, playerId);
            ps.setInt(3, questionId);
            ps.setLong(4, Math.max(0, elapsedMs));
            ps.setInt(5, timedOut ? 1 : 0);

            ps.executeUpdate();
        }
    }

    public void remove(
            int sessionId,
            int playerId,
            int questionId) throws SQLException {

        try (
            Connection c = Database.getConnection();
            PreparedStatement ps = c.prepareStatement("""
                DELETE FROM timed_answers
                WHERE session_id = ?
                  AND player_id = ?
                  AND question_id = ?
                """)
        ) {
            ps.setInt(1, sessionId);
            ps.setInt(2, playerId);
            ps.setInt(3, questionId);

            ps.executeUpdate();
        }
    }

    public List<Ranking> ranking(int sessionId)
            throws SQLException {

        List<Ranking> result = new ArrayList<>();

        try (
            Connection c = Database.getConnection();
            PreparedStatement ps = c.prepareStatement("""
                SELECT
                    sp.player_id,
                    p.name,
                    sp.turn_order,

                    COALESCE((
                        SELECT SUM(s.points)
                        FROM scores s
                        WHERE s.session_id = sp.session_id
                          AND s.player_id = sp.player_id
                    ), 0) AS points,

                    COALESCE((
                        SELECT SUM(t.elapsed_ms)
                        FROM timed_answers t
                        WHERE t.session_id = sp.session_id
                          AND t.player_id = sp.player_id
                    ), 0) AS ms

                FROM session_players sp

                JOIN players p
                  ON p.id = sp.player_id

                WHERE sp.session_id = ?

                ORDER BY
                    points DESC,
                    ms ASC,
                    sp.turn_order ASC
                """)
        ) {
            ps.setInt(1, sessionId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    result.add(new Ranking(
                        rs.getInt("player_id"),
                        rs.getString("name"),
                        rs.getInt("points"),
                        rs.getLong("ms"),
                        rs.getInt("turn_order")
                    ));
                }
            }
        }

        return result;
    }
}