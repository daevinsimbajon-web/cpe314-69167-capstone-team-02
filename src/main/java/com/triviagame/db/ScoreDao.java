
package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScoreDao {

    // Represents a saved score record
    public record ScoreRecord(
            int id,
            int sessionId,
            int playerId,
            int categoryId,
            int questionId,
            boolean isCorrect,
            int points
    ) {
    }

    // CREATE: Start a new game session
    public int createGameSession() throws SQLException {

        String sql = """
            INSERT INTO game_sessions (played_at)
            VALUES (CURRENT_TIMESTAMP)
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            int rows = statement.executeUpdate();

            if (rows != 1) {
                throw new SQLException(
                        "Failed to create game session"
                );
            }

            return getLastInsertedId(connection);
        }
    }

    // INSERT: Save a player's answer and score
    public int addScore(
            int sessionId,
            int playerId,
            int categoryId,
            int questionId,
            boolean isCorrect,
            int points
    ) throws SQLException {

        if (sessionId <= 0 || playerId <= 0
                || categoryId <= 0 || questionId <= 0) {
            throw new IllegalArgumentException(
                    "All IDs must be positive"
            );
        }

        if (points < 0) {
            throw new IllegalArgumentException(
                    "Points cannot be negative"
            );
        }

        String sql = """
            INSERT INTO scores (
                session_id,
                player_id,
                category_id,
                question_id,
                is_correct,
                points
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);
            statement.setInt(2, playerId);
            statement.setInt(3, categoryId);
            statement.setInt(4, questionId);
            statement.setInt(5, isCorrect ? 1 : 0);
            statement.setInt(6, points);

            int rows = statement.executeUpdate();

            if (rows != 1) {
                throw new SQLException(
                        "Failed to save score"
                );
            }

            return getLastInsertedId(connection);
        }
    }

    // SELECT: Find a score by ID
    public Optional<ScoreRecord> getScoreById(int id)
            throws SQLException {

        String sql = """
            SELECT id, session_id, player_id,
                   category_id, question_id,
                   is_correct, points
            FROM scores
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (result.next()) {
                    return Optional.of(
                            mapScore(result)
                    );
                }
            }
        }

        return Optional.empty();
    }

    // SELECT: Find scores belonging to one player
    public List<ScoreRecord> getScoresByPlayer(
            int playerId
    ) throws SQLException {

        String sql = """
            SELECT id, session_id, player_id,
                   category_id, question_id,
                   is_correct, points
            FROM scores
            WHERE player_id = ?
            ORDER BY id ASC
            """;

        return getScoreList(sql, playerId);
    }

    // SELECT: Get scores from one game session
    public List<ScoreRecord> getScoresBySession(
            int sessionId
    ) throws SQLException {

        String sql = """
            SELECT id, session_id, player_id,
                   category_id, question_id,
                   is_correct, points
            FROM scores
            WHERE session_id = ?
            ORDER BY id ASC
            """;

        return getScoreList(sql, sessionId);
    }

    // SELECT: Calculate total points for a player
    public long getTotalPointsByPlayer(
            int playerId
    ) throws SQLException {

        String sql = """
            SELECT COALESCE(SUM(points), 0) AS total
            FROM scores
            WHERE player_id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, playerId);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (result.next()) {
                    return result.getLong("total");
                }
            }
        }

        return 0;
    }

    // UPDATE: Change the score for an answer
    public boolean updateScore(
            int scoreId,
            boolean isCorrect,
            int points
    ) throws SQLException {

        if (scoreId <= 0 || points < 0) {
            throw new IllegalArgumentException(
                    "Invalid score ID or points"
            );
        }

        String sql = """
            UPDATE scores
            SET is_correct = ?,
                points = ?
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, isCorrect ? 1 : 0);
            statement.setInt(2, points);
            statement.setInt(3, scoreId);

            return statement.executeUpdate() > 0;
        }
    }

    // DELETE: Remove a score
    public boolean deleteScore(int scoreId)
            throws SQLException {

        String sql = """
            DELETE FROM scores
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, scoreId);

            return statement.executeUpdate() > 0;
        }
    }

    // DELETE: Remove an unused game session
    public boolean deleteGameSession(int sessionId)
            throws SQLException {

        String sql = """
            DELETE FROM game_sessions
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, sessionId);

            return statement.executeUpdate() > 0;
        }
    }

    // Helper: Retrieve scores using a parameterized query
    private List<ScoreRecord> getScoreList(
            String sql,
            int id
    ) throws SQLException {

        List<ScoreRecord> scores = new ArrayList<>();

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    scores.add(mapScore(result));
                }
            }
        }

        return scores;
    }

    // Helper: Convert SQL row into ScoreRecord
    private ScoreRecord mapScore(
            ResultSet result
    ) throws SQLException {

        return new ScoreRecord(
                result.getInt("id"),
                result.getInt("session_id"),
                result.getInt("player_id"),
                result.getInt("category_id"),
                result.getInt("question_id"),
                result.getInt("is_correct") == 1,
                result.getInt("points")
        );
    }

    // Helper: Get ID generated by the last INSERT
    private int getLastInsertedId(
            Connection connection
    ) throws SQLException {

        String sql =
                "SELECT last_insert_rowid() AS id";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result =
                     statement.executeQuery()) {

            if (result.next()) {
                return result.getInt("id");
            }
        }

        throw new SQLException(
                "Generated ID could not be retrieved"
        );
    }
}
