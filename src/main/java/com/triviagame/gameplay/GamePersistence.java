package com.triviagame.gameplay;

import com.triviagame.model.GamePlayer;
import java.sql.SQLException;
import java.util.List;

/** Storage operations a desktop session needs after setup. */
public interface GamePersistence extends GameEngine.ScoreStore {
  int createSession(List<GamePlayer> players) throws SQLException;

  boolean discardNewEmptySession(int sessionId) throws SQLException;

  void saveTiming(int sessionId, int playerId, int questionId, long elapsedMs, boolean timedOut)
      throws SQLException;

  void removeTiming(int sessionId, int playerId, int questionId) throws SQLException;

  List<GamePlayer.Ranking> rankings(int sessionId) throws SQLException;
}
