
package com.triviagame.players;

import java.util.List;
import java.util.function.Consumer;

import com.triviagame.db.PlayerDao;
import com.triviagame.db.ScoreDao;
import com.triviagame.tasks.BackgroundTaskRunner;

import javafx.concurrent.Task;

public class PlayersController {

    private final PlayerDao playerDao = new PlayerDao();
    private final ScoreDao scoreDao = new ScoreDao();

    public void loadPlayers(
            Consumer<List<PlayerDao.PlayerRecord>> onSuccess,
            Consumer<Throwable> onError) {

        Task<List<PlayerDao.PlayerRecord>> task = new Task<>() {
            @Override
            protected List<PlayerDao.PlayerRecord> call()
                    throws Exception {
                return playerDao.getAllPlayers();
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void addPlayer(
            String name,
            Consumer<Integer> onSuccess,
            Consumer<Throwable> onError) {

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return playerDao.addPlayer(name);
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void deletePlayerIfNoScores(
            int playerId,
            Consumer<Boolean> onSuccess,
            Consumer<Throwable> onError) {

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {

                if (!scoreDao.getScoresByPlayer(playerId).isEmpty()) {
                    return false;
                }

                return playerDao.deletePlayer(playerId);
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }
}
