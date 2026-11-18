package com.triviagame.controller;

import com.triviagame.model.AnalyticsRows.SessionPoints;
import com.triviagame.service.AnalyticsService;
import java.util.List;
import javafx.concurrent.Task;

/** Prepares analytics queries without reading SQLite on the JavaFX thread. */
public final class AnalyticsController {
  private final AnalyticsService analytics = new AnalyticsService();

  public Task<AnalyticsService.Snapshot> load(Integer selectedPlayerId) {
    return new WorkerTask<>(() -> analytics.load(selectedPlayerId));
  }

  public Task<List<SessionPoints>> sessions(int playerId) {
    return new WorkerTask<>(() -> analytics.sessions(playerId));
  }
}
