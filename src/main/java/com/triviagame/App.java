package com.triviagame;

import com.triviagame.db.Database;
import com.triviagame.db.SampleQuestionSeeder;
import com.triviagame.home.Home;
import com.triviagame.home.Ui;
import com.triviagame.tasks.BackgroundTaskRunner;

import java.net.URL;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private Home home;

    @Override
    public void start(Stage stage) {
        try {
            Database.initializeSchema();
            SampleQuestionSeeder.seed();

            home = new Home(stage);

            Scene scene = new Scene(home.getView(), 1280, 800);

            // All colors and styles live in src/main/resources/style.css
            URL stylesheet = App.class.getResource("/style.css");
            if (stylesheet != null) {
                scene.getStylesheets().add(stylesheet.toExternalForm());
            }

            stage.setTitle("Trivia Night Manager");
            stage.setMinWidth(1100);
            stage.setMinHeight(700);
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            // Show the problem to the user instead of failing silently.
            Ui.error("The app could not start:\n" + e.getMessage());
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (home != null) {
            home.shutdown();
        }
        BackgroundTaskRunner.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
