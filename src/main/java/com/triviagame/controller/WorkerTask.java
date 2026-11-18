package com.triviagame.controller;

import java.util.concurrent.Callable;
import javafx.concurrent.Task;

/** Adapts a synchronous operation to the application's JavaFX executor. */
final class WorkerTask<T> extends Task<T> {
  private final Callable<T> operation;

  WorkerTask(Callable<T> operation) {
    this.operation = operation;
  }

  @Override
  protected T call() throws Exception {
    return operation.call();
  }
}
