package com.triviagame.dao;

import com.triviagame.gameplay.GamePersistence;
import com.triviagame.model.GamePlayer;
import java.sql.SQLException;
import java.util.List;

/** Adapts SQLite session, score, and timing operations to the gameplay model. */
public final class SqliteGamePersistence implements GamePersistence {
  private final SessionPlayerDao sessions = new SessionPlayerDao();
  private final TimedAnswerDao timings = new TimedAnswerDao();
  private final ScoreDao scores = new ScoreDao();

  /** Initializes tables and resolves player identities before play. */
  public List<GamePlayer> preparePlayers(List<GamePlayer.Setup> players) throws SQLException {
    timings.initialize();
    return sessions.preparePlayers(players);
  }

  @Override
  public int createSession(List<GamePlayer> players) throws SQLException {
    return sessions.createSession(players);
  }

  @Override
  public boolean discardNewEmptySession(int sessionId) throws SQLException {
    return sessions.discardNewEmptySession(sessionId);
  }

  @Override
  public int addScore(
      int sessionId, int playerId, int categoryId, int questionId, boolean correct, int points)
      throws SQLException {
    return scores.addScore(sessionId, playerId, categoryId, questionId, correct, points);
  }

  @Override
  public boolean deleteScore(int scoreId) throws SQLException {
    return scores.deleteScore(scoreId);
  }

  @Override
  public void saveTiming(
      int sessionId, int playerId, int questionId, long elapsedMs, boolean timedOut)
      throws SQLException {
    timings.save(sessionId, playerId, questionId, elapsedMs, timedOut);
  }

  @Override
  public void removeTiming(int sessionId, int playerId, int questionId) throws SQLException {
    timings.remove(sessionId, playerId, questionId);
  }

  @Override
  public List<GamePlayer.Ranking> rankings(int sessionId) throws SQLException {
    return timings.ranking(sessionId);
  }
}
