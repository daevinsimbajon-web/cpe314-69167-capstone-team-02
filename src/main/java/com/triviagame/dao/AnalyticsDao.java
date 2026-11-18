package com.triviagame.dao;

import com.triviagame.model.AnalyticsRows.BestCategory;
import com.triviagame.model.AnalyticsRows.CategoryAccuracy;
import com.triviagame.model.AnalyticsRows.GameAverage;
import com.triviagame.model.AnalyticsRows.PlayerAverage;
import com.triviagame.model.AnalyticsRows.PlayerTotal;
import com.triviagame.model.AnalyticsRows.SessionPoints;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsDao {

  // SELECT: Accuracy per category (categories without scores are omitted)
  public List<CategoryAccuracy> getAccuracyByCategory() throws SQLException {

    List<CategoryAccuracy> results = new ArrayList<>();

    String sql =
        """
        SELECT c.name,
               COUNT(s.id) AS total_answers,
               SUM(s.is_correct) AS correct_answers,
               ROUND(100.0 * SUM(s.is_correct)
                     / COUNT(s.id), 2) AS accuracy_pct
        FROM categories c
        JOIN questions q ON q.category_id = c.id
        JOIN scores s ON s.question_id = q.id
        GROUP BY c.id, c.name
        ORDER BY accuracy_pct DESC, c.name ASC
        """;

    try (Connection connection = Database.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet result = statement.executeQuery()) {

      while (result.next()) {
        results.add(
            new CategoryAccuracy(
                result.getString("name"),
                result.getInt("total_answers"),
                result.getInt("correct_answers"),
                result.getDouble("accuracy_pct")));
      }
    }

    return results;
  }

  // SELECT: Players ranked by total points
  public List<PlayerTotal> getTopPlayers(int limit) throws SQLException {

    if (limit <= 0) {
      throw new IllegalArgumentException("Limit must be positive");
    }

    List<PlayerTotal> results = new ArrayList<>();

    String sql =
        """
        SELECT p.id, p.name,
               SUM(s.points) AS total_points
        FROM players p
        JOIN scores s ON s.player_id = p.id
        GROUP BY p.id, p.name
        ORDER BY total_points DESC, p.name ASC
        LIMIT ?
        """;

    try (Connection connection = Database.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setInt(1, limit);

      try (ResultSet result = statement.executeQuery()) {

        while (result.next()) {
          results.add(
              new PlayerTotal(
                  result.getInt("id"), result.getString("name"), result.getLong("total_points")));
        }
      }
    }

    return results;
  }

  // SELECT: Average points per answer for each player
  public List<PlayerAverage> getAveragePointsPerPlayer() throws SQLException {

    List<PlayerAverage> results = new ArrayList<>();

    String sql =
        """
        SELECT p.id, p.name,
               AVG(s.points) AS avg_points
        FROM players p
        JOIN scores s ON s.player_id = p.id
        GROUP BY p.id, p.name
        ORDER BY avg_points DESC, p.name ASC
        """;

    try (Connection connection = Database.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet result = statement.executeQuery()) {

      while (result.next()) {
        results.add(
            new PlayerAverage(
                result.getInt("id"), result.getString("name"), result.getDouble("avg_points")));
      }
    }

    return results;
  }

  // SELECT: Points a player earned in each session, oldest first
  public List<SessionPoints> getPointsPerSession(int playerId) throws SQLException {

    if (playerId <= 0) {
      throw new IllegalArgumentException("Player ID must be positive");
    }

    List<SessionPoints> results = new ArrayList<>();

    String sql =
        """
        SELECT gs.played_at,
               SUM(s.points) AS points
        FROM scores s
        JOIN game_sessions gs ON gs.id = s.session_id
        WHERE s.player_id = ?
        GROUP BY gs.id, gs.played_at
        ORDER BY gs.played_at ASC, gs.id ASC
        """;

    try (Connection connection = Database.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setInt(1, playerId);

      try (ResultSet result = statement.executeQuery()) {

        while (result.next()) {
          results.add(new SessionPoints(result.getString("played_at"), result.getLong("points")));
        }
      }
    }

    return results;
  }

  // SELECT: The best category for every player.
  // Best = highest accuracy; ties go to the category with more
  // answers, then to the name that comes first alphabetically.
  // Players without scores are omitted.
  public List<BestCategory> getBestCategoryPerPlayer() throws SQLException {

    List<BestCategory> results = new ArrayList<>();

    String sql =
        """
        WITH stats AS (
            SELECT p.id AS player_id,
                   p.name AS player_name,
                   c.name AS category_name,
                   COUNT(s.id) AS total_answers,
                   SUM(s.is_correct) AS correct_answers,
                   100.0 * SUM(s.is_correct)
                         / COUNT(s.id) AS accuracy
            FROM players p
            JOIN scores s ON s.player_id = p.id
            JOIN questions q ON q.id = s.question_id
            JOIN categories c ON c.id = q.category_id
            GROUP BY p.id, c.id, c.name
        ),
        ranked AS (
            SELECT *,
                   ROW_NUMBER() OVER (
                       PARTITION BY player_id
                       ORDER BY accuracy DESC,
                                total_answers DESC,
                                category_name ASC
                   ) AS rank_in_player
            FROM stats
        )
        SELECT player_id, player_name, category_name, total_answers,
               correct_answers, ROUND(accuracy, 2) AS accuracy_pct
        FROM ranked
        WHERE rank_in_player = 1
        ORDER BY accuracy_pct DESC, player_name ASC, player_id ASC
        """;

    try (Connection connection = Database.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet result = statement.executeQuery()) {

      while (result.next()) {
        results.add(
            new BestCategory(
                result.getInt("player_id"),
                result.getString("player_name"),
                result.getString("category_name"),
                result.getInt("total_answers"),
                result.getInt("correct_answers"),
                result.getDouble("accuracy_pct")));
      }
    }

    return results;
  }

  // SELECT: Average points per game. A "game" is one player in one
  // session. With no saved answers: 0 games, average 0.
  public GameAverage getAveragePointsPerGame() throws SQLException {

    String sql =
        """
        SELECT COUNT(*) AS games,
               AVG(game_points) AS average_points,
               MAX(game_points) AS best_points
        FROM (
            SELECT SUM(points) AS game_points
            FROM scores
            GROUP BY session_id, player_id
        )
        """;

    try (Connection connection = Database.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet result = statement.executeQuery()) {

      if (!result.next()) {
        return new GameAverage(0, 0.0, 0);
      }

      return new GameAverage(
          result.getInt("games"),
          result.getDouble("average_points"),
          result.getLong("best_points"));
    }
  }
}
