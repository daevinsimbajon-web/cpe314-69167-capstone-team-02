
package com.triviagame.m2;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.QuestionBankService;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.db.TimedAnswerDao;
import com.triviagame.tasks.BackgroundTaskRunner;

import javafx.concurrent.Task;

public final class M2GameController {

    private final QuestionBankService bank =
            new QuestionBankService();

    private final CategoryDao categoryDao =
            new CategoryDao();

    public record BankData(
            int questionCount,
            List<CategoryDao.CategoryRecord> categories,
            boolean apiPreloaded) {
    }

    public void loadBankData(
            Consumer<BankData> onSuccess,
            Consumer<Throwable> onError) {

        Task<BankData> task = new Task<>() {
            @Override
            protected BankData call() throws Exception {

                boolean preloaded = bank.apiPreloaded();

                return new BankData(
                        bank.count(),
                        categoryDao.getAllCategories(),
                        preloaded
                );
            }
        };

        task.setOnSucceeded(e ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(e ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void loadQuestions(
            int categoryId,
            Consumer<List<QuestionDao.QuestionRecord>> onSuccess,
            Consumer<Throwable> onError) {

        Task<List<QuestionDao.QuestionRecord>> task =
                new Task<>() {
            @Override
            protected List<QuestionDao.QuestionRecord> call()
                    throws Exception {
                return bank.getQuestions(categoryId);
            }
        };

        task.setOnSucceeded(e ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(e ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void startGame(
            List<SessionPlayerDao.PlayerSetup> players,
            List<QuestionDao.QuestionRecord> questions,
            int questionsPerPlayer,
            Consumer<RoundRobinGame> onSuccess,
            Consumer<Throwable> onError) {

        Task<RoundRobinGame> task = new Task<>() {
            @Override
            protected RoundRobinGame call() throws Exception {
                return new RoundRobinGame(
                        players,
                        questions,
                        questionsPerPlayer
                );
            }
        };

        task.setOnSucceeded(e ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(e ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void submitAnswer(
            RoundRobinGame game,
            String answer,
            long elapsedMs,
            boolean timedOut,
            Consumer<RoundRobinGame.AnswerResult> onSuccess,
            Consumer<Throwable> onError) {

        Task<RoundRobinGame.AnswerResult> task =
                new Task<>() {
            @Override
            protected RoundRobinGame.AnswerResult call()
                    throws Exception {

                synchronized (game) {
                    return game.answer(
                            answer,
                            elapsedMs,
                            timedOut
                    );
                }
            }
        };

        task.setOnSucceeded(e ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(e ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void undoAnswer(
            RoundRobinGame game,
            Consumer<Boolean> onSuccess,
            Consumer<Throwable> onError) {

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                synchronized (game) {
                    return game.undoLast();
                }
            }
        };

        task.setOnSucceeded(e ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(e ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void loadLeaderboard(
            RoundRobinGame game,
            Consumer<List<TimedAnswerDao.Ranking>> onSuccess,
            Consumer<Throwable> onError) {

        Task<List<TimedAnswerDao.Ranking>> task =
                new Task<>() {
            @Override
            protected List<TimedAnswerDao.Ranking> call()
                    throws Exception {
                synchronized (game) {
                    return game.leaderboard();
                }
            }
        };

        task.setOnSucceeded(e ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(e ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }
}
