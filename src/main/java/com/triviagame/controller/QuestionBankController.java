package com.triviagame.controller;

import com.triviagame.service.DashboardService;
import com.triviagame.tasks.FetchQuestionsTask;
import javafx.concurrent.Task;

/** Prepares independent dashboard and question-download work for the UI executor. */
public final class QuestionBankController {
  private final DashboardService dashboard = new DashboardService();

  public Task<DashboardService.Counts> counts() {
    return new WorkerTask<>(dashboard::counts);
  }

  public FetchQuestionsTask download(int amount, String difficulty, String type) {
    return new FetchQuestionsTask(amount, difficulty, type);
  }
}
