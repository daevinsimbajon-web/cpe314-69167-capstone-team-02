package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsDao {

    // Accuracy summary for one category
    public record CategoryAccuracy(
            String categoryName,
            int totalAnswers,
            int correctAnswers,
            double accuracyPercent
    ) {
    }

    // Total points earned by one player
    public record PlayerTotal(
            String playerName,
            long totalPoints
    ) {
    }

    // Average points per answer for one player
    public record PlayerAverage(
            String playerName,
            double averagePoints
    ) {
    }

    // Points a player earned in one game session
    public record SessionPoints(
            String playedAt,
            long points
    ) {
    }

    // SELECT: Accuracy per category (categories without scores are omitted)
    public List<CategoryAccuracy> getAccuracyByCategory()
            throws SQLException {

        List<CategoryAccuracy> results = new ArrayList<>();

        String sql = """
            SELECT c.name,
                   COUNT(s.id) AS total_answers,
                   SUM(s.is_correct) AS correct_answers,
                   ROUND(100.0 * SUM(s.is_correct)
                         / COUNT(s.id), 2) AS accuracy_pct
            FROM categories c
            JOIN scores s ON s.category_id = c.id
            GROUP BY c.id, c.name
            ORDER BY accuracy_pct DESC, c.name ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result =
                     statement.executeQuery()) {

            while (result.next()) {
                results.add(new CategoryAccuracy(
                        result.getString("name"),
                        result.getInt("total_answers"),
                        result.getInt("correct_answers"),
                        result.getDouble("accuracy_pct")
                ));
            }
        }

        return results;
    }

    // SELECT: Players ranked by total points
    public List<PlayerTotal> getTopPlayers(int limit)
            throws SQLException {

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Limit must be positive"
            );
        }

        List<PlayerTotal> results = new ArrayList<>();

        String sql = """
            SELECT p.name,
                   SUM(s.points) AS total_points
            FROM players p
            JOIN scores s ON s.player_id = p.id
            GROUP BY p.id, p.name
            ORDER BY total_points DESC, p.name ASC
            LIMIT ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, limit);

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    results.add(new PlayerTotal(
                            result.getString("name"),
                            result.getLong("total_points")
                    ));
                }
            }
        }

        return results;
    }

    // SELECT: Average points per answer for each player
    public List<PlayerAverage> getAveragePointsPerPlayer()
            throws SQLException {

        List<PlayerAverage> results = new ArrayList<>();

        String sql = """
            SELECT p.name,
                   AVG(s.points) AS avg_points
            FROM players p
            JOIN scores s ON s.player_id = p.id
            GROUP BY p.id, p.name
            ORDER BY avg_points DESC, p.name ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result =
                     statement.executeQuery()) {

            while (result.next()) {
                results.add(new PlayerAverage(
                        result.getString("name"),
                        result.getDouble("avg_points")
                ));
            }
        }

        return results;
    }

    // SELECT: Points a player earned in each session, oldest first
    public List<SessionPoints> getPointsPerSession(int playerId)
            throws SQLException {

        if (playerId <= 0) {
            throw new IllegalArgumentException(
                    "Player ID must be positive"
            );
        }

        List<SessionPoints> results = new ArrayList<>();

        String sql = """
            SELECT gs.played_at,
                   SUM(s.points) AS points
            FROM scores s
            JOIN game_sessions gs ON gs.id = s.session_id
            WHERE s.player_id = ?
            GROUP BY gs.id, gs.played_at
            ORDER BY gs.played_at ASC, gs.id ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, playerId);

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    results.add(new SessionPoints(
                            result.getString("played_at"),
                            result.getLong("points")
                    ));
                }
            }
        }

        return results;
    }
}
