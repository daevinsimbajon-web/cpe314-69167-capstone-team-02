package com.triviagame.controller;

import com.triviagame.model.PlayerRecord;
import com.triviagame.service.PlayerService;
import java.util.List;
import javafx.concurrent.Task;

/** Prepares background operations for the player management screen. */
public final class PlayerController {
  private final PlayerService players = new PlayerService();

  public Task<List<PlayerRecord>> list() {
    return new WorkerTask<>(players::all);
  }

  public Task<Void> add(String name) {
    return new WorkerTask<>(
        () -> {
          players.add(name.trim());
          return null;
        });
  }

  public Task<Boolean> deleteIfUnscored(int playerId) {
    return new WorkerTask<>(() -> players.deleteIfUnscored(playerId));
  }
}
