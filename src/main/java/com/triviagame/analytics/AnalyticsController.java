
package com.triviagame.analytics;

import java.util.List;
import java.util.function.Consumer;

import com.triviagame.db.AnalyticsDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.tasks.BackgroundTaskRunner;

import javafx.concurrent.Task;

public class AnalyticsController {

    private final AnalyticsDao analyticsDao = new AnalyticsDao();
    private final PlayerDao playerDao = new PlayerDao();

    public record AnalyticsData(
            List<PlayerDao.PlayerRecord> players,
            List<AnalyticsDao.CategoryAccuracy> accuracy
    ) {
    }

    public void loadAnalytics(
            Consumer<AnalyticsData> onSuccess,
            Consumer<Throwable> onError) {

        Task<AnalyticsData> task = new Task<>() {
            @Override
            protected AnalyticsData call() throws Exception {

                List<PlayerDao.PlayerRecord> players =
                        playerDao.getAllPlayers();

                List<AnalyticsDao.CategoryAccuracy> accuracy =
                        analyticsDao.getAccuracyByCategory();

                return new AnalyticsData(players, accuracy);
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void loadSessions(
            int playerId,
            Consumer<List<AnalyticsDao.SessionPoints>> onSuccess,
            Consumer<Throwable> onError) {

        Task<List<AnalyticsDao.SessionPoints>> task = new Task<>() {
            @Override
            protected List<AnalyticsDao.SessionPoints> call()
                    throws Exception {

                return analyticsDao.getPointsPerSession(playerId);
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }
}
