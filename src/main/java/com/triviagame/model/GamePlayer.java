package com.triviagame.model;

/** A registered player and their settings for a desktop game. */
public record GamePlayer(int playerId, String name, int secondsPerQuestion, int turnOrder) {
  /** A new or existing player selected during game setup. */
  public record Setup(String name, int secondsPerQuestion, Integer playerId) {
    public Setup(String name, int secondsPerQuestion) {
      this(name, secondsPerQuestion, null);
    }

    public Setup {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Player name required");
      }
      name = name.trim();
      if (secondsPerQuestion < 5 || secondsPerQuestion > 300) {
        throw new IllegalArgumentException("Each player needs 5–300 seconds per question");
      }
      if (playerId != null && playerId <= 0) {
        throw new IllegalArgumentException("Invalid player ID");
      }
    }
  }

  /** A player's rank data, including the elapsed time used for ties. */
  public record Ranking(int playerId, String name, int points, long elapsedMillis, int turnOrder) {}
}
