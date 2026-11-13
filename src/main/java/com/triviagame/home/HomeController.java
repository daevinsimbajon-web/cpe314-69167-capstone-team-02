
package com.triviagame.home;

import java.util.function.Consumer;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.ScoreDao;
import com.triviagame.tasks.BackgroundTaskRunner;

import javafx.concurrent.Task;

public class HomeController {

    private final PlayerDao playerDao = new PlayerDao();
    private final ScoreDao scoreDao = new ScoreDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final CategoryDao categoryDao = new CategoryDao();

    public record DashboardStats(
            int totalPlayers,
            int gamesPlayed,
            int totalQuestions,
            int totalCategories
    ) {
    }

    public void loadDashboard(
            Consumer<DashboardStats> onSuccess,
            Consumer<Throwable> onError) {

        Task<DashboardStats> task = new Task<>() {

            @Override
            protected DashboardStats call() throws Exception {

                int players = playerDao.getAllPlayers().size();

                int games = scoreDao.countPlayedSessions();

                int questions = questionDao.getAllQuestions().size();

                int categories = categoryDao.getAllCategories().size();

                return new DashboardStats(
                        players,
                        games,
                        questions,
                        categories
                );
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }
}
