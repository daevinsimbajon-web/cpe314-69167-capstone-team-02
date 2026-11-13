package com.triviagame.tasks;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javafx.concurrent.Task;

public final class BackgroundTaskRunner {

    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "trivia-background-worker");
        thread.setDaemon(true);
        return thread;
    };

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(3, THREAD_FACTORY);

    private BackgroundTaskRunner() {
    }

    public static <T> void run(Task<T> task) {
        EXECUTOR.execute(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
