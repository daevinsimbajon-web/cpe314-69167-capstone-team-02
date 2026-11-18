package com.triviagame.service;

import com.triviagame.dao.AnalyticsDao;
import com.triviagame.dao.PlayerDao;
import com.triviagame.model.AnalyticsRows;
import com.triviagame.model.PlayerRecord;
import java.sql.SQLException;
import java.util.List;

/** Fetches persisted analytics without depending on JavaFX. */
public final class AnalyticsService {
  public record Snapshot(
      List<PlayerRecord> players,
      List<AnalyticsRows.CategoryAccuracy> accuracy,
      List<AnalyticsRows.BestCategory> bestCategories,
      PlayerRecord selected,
      List<AnalyticsRows.SessionPoints> sessions) {}

  private final AnalyticsDao analytics = new AnalyticsDao();
  private final PlayerDao players = new PlayerDao();

  public Snapshot load(Integer selectedId) throws SQLException {
    var all = players.getAllPlayers();
    var selected =
        all.stream()
            .filter(p -> selectedId != null && p.id() == selectedId)
            .findFirst()
            .orElse(all.isEmpty() ? null : all.get(0));
    return new Snapshot(
        all,
        analytics.getAccuracyByCategory(),
        analytics.getBestCategoryPerPlayer(),
        selected,
        selected == null ? List.of() : analytics.getPointsPerSession(selected.id()));
  }

  public List<AnalyticsRows.SessionPoints> sessions(int playerId) throws SQLException {
    return analytics.getPointsPerSession(playerId);
  }
}
