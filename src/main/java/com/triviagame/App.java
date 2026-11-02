
package com.triviagame;

import com.triviagame.db.Database;
import com.triviagame.db.SampleQuestionSeeder;
import com.triviagame.home.Home;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        try {
            Database.initializeSchema();
            SampleQuestionSeeder.seed();

            Home home = new Home(stage);

            Scene scene = new Scene(home.getView(), 1440, 900);

            stage.setTitle("Trivia Night Manager");
            stage.setMinWidth(1100);
            stage.setMinHeight(700);
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
