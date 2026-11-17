package com.triviagame.service;

import com.triviagame.db.AnalyticsDao;
import com.triviagame.db.PlayerDao;
import java.sql.SQLException;
import java.util.List;

/** Fetches persisted analytics without depending on JavaFX. */
public final class AnalyticsService {
    public record Snapshot(List<PlayerDao.PlayerRecord> players,
            List<AnalyticsDao.CategoryAccuracy> accuracy,
            List<AnalyticsDao.BestCategory> bestCategories,
            PlayerDao.PlayerRecord selected,
            List<AnalyticsDao.SessionPoints> sessions) {}
    private final AnalyticsDao analytics = new AnalyticsDao();
    private final PlayerDao players = new PlayerDao();

    public Snapshot load(Integer selectedId) throws SQLException {
        var all = players.getAllPlayers();
        var selected = all.stream().filter(p -> selectedId != null && p.id() == selectedId)
                .findFirst().orElse(all.isEmpty() ? null : all.get(0));
        return new Snapshot(all, analytics.getAccuracyByCategory(), analytics.getBestCategoryPerPlayer(), selected,
                selected == null ? List.of() : analytics.getPointsPerSession(selected.id()));
    }

    public List<AnalyticsDao.SessionPoints> sessions(int playerId) throws SQLException {
        return analytics.getPointsPerSession(playerId);
    }
}
