package com.triviagame;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        // TODO (Member 4): replace this label with the Home screen
        StackPane root = new StackPane(new Label("Trivia & Quiz Game Night Manager"));
        stage.setTitle("Trivia & Quiz Game Night Manager");
        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}