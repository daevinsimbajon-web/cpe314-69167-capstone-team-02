package com.triviagame.m2;

import com.triviagame.db.QuestionBankService;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class M2DemoApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        new QuestionBankService().initializeBank();

        stage.setTitle(
            "Trivia Night — Milestone 2 Demo"
        );

        stage.setScene(
            new Scene(
                new M2GameScreen().getView(),
                1000,
                800
            )
        );

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}