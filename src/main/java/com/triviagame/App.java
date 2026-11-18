package com.triviagame;

import com.triviagame.controller.GameController;
import com.triviagame.controller.ScoreHistoryController;
import com.triviagame.dao.Database;
import com.triviagame.dao.SampleQuestionSeeder;
import com.triviagame.ui.home.Home;
import com.triviagame.ui.home.Ui;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

  private Home home;
  private final ExecutorService workers =
      Executors.newFixedThreadPool(
          3,
          job -> {
            Thread thread = new Thread(job, "hootly-desktop-worker");
            thread.setDaemon(true);
            return thread;
          });

  @Override
  public void start(Stage stage) {
    Scene scene = new Scene(new StackPane(new Label("Loading your quiz data…")), 1280, 800);
    URL stylesheet = App.class.getResource("/style.css");
    if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
    stage.setTitle("Trivia Night Manager");
    stage.setMinWidth(1100);
    stage.setMinHeight(700);
    stage.setScene(scene);
    stage.show();

    Task<Void> initialize =
        new Task<>() {
          @Override
          protected Void call() throws Exception {
            Database.initializeSchema();
            SampleQuestionSeeder.seed();
            return null;
          }
        };
    initialize.setOnSucceeded(
        event -> {
          home = new Home(workers, new GameController(), new ScoreHistoryController());
          scene.setRoot(home.getView());
        });
    initialize.setOnFailed(
        event -> {
          Logger.getLogger(App.class.getName())
              .log(Level.SEVERE, "Desktop startup failed", initialize.getException());
          Ui.error("The app could not start. Please check the database and try again.");
          Platform.exit();
        });
    workers.execute(initialize);
  }

  @Override
  public void stop() {
    if (home != null) {
      home.shutdown();
    }
    workers.shutdownNow();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
