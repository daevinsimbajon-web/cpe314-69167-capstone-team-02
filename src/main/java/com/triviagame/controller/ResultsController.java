package com.triviagame.controller;

import com.triviagame.service.LeaderboardService;
import java.util.List;
import javafx.concurrent.Task;

/** Prepares the stored leaderboard query for the results screen. */
public final class ResultsController {
  private final LeaderboardService results = new LeaderboardService();

  public Task<List<LeaderboardService.Entry>> leaderboard() {
    return new WorkerTask<>(results::leaderboard);
  }
}
