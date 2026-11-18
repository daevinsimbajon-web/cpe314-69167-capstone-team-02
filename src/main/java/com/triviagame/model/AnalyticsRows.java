package com.triviagame.model;

/** Summaries from the persisted desktop scoreboard. */
public final class AnalyticsRows {
  private AnalyticsRows() {}

  // Accuracy summary for one category
  public record CategoryAccuracy(
      String categoryName, int totalAnswers, int correctAnswers, double accuracyPercent) {}

  // Total points earned by one player
  public record PlayerTotal(int playerId, String playerName, long totalPoints) {}

  // Average points per answer for one player
  public record PlayerAverage(int playerId, String playerName, double averagePoints) {}

  // Points a player earned in one game session
  public record SessionPoints(String playedAt, long points) {}

  // The category one player answers best (by accuracy)
  public record BestCategory(
      int playerId,
      String playerName,
      String categoryName,
      int totalAnswers,
      int correctAnswers,
      double accuracyPercent) {}

  // Average points a player earns in one game (one session),
  // taken over every player-game that has saved answers
  public record GameAverage(int games, double averagePoints, long bestGamePoints) {}
}
