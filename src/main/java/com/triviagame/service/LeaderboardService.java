package com.triviagame.service;

import com.triviagame.db.AnalyticsDao;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds leaderboard rows by permanent player ID, including duplicate display names. */
public final class LeaderboardService {
    public record Entry(int rank, int playerId, String player, long points, double average) {}
    private final AnalyticsDao dao = new AnalyticsDao();

    public List<Entry> leaderboard() throws SQLException {
        var totals = dao.getTopPlayers(100);
        Map<Integer, Double> averages = new HashMap<>();
        for (var row : dao.getAveragePointsPerPlayer()) averages.put(row.playerId(), row.averagePoints());
        List<Entry> result = new ArrayList<>();
        int rank = 0; long previous = Long.MIN_VALUE;
        for (int i = 0; i < totals.size(); i++) {
            var row = totals.get(i);
            if (row.totalPoints() != previous) { rank = i + 1; previous = row.totalPoints(); }
            result.add(new Entry(rank, row.playerId(), row.playerName(), row.totalPoints(),
                    averages.getOrDefault(row.playerId(), 0.0)));
        }
        return result;
    }
}
