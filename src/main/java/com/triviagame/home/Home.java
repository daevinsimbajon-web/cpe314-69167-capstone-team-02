package com.triviagame.home;

import com.triviagame.players.Players;
import java.sql.SQLException;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.CategoryDao;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class Home {

    private int getPlayerCount() {
        try {
            return new PlayerDao().getAllPlayers().size();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int getQuestionCount() {
        try {
            return new QuestionDao().getAllQuestions().size();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int getCategoryCount() {
        try {
            return new CategoryDao().getAllCategories().size();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private final BorderPane root = new BorderPane();

    private final String background = "#10111F";
    private final String card = "#1B1D30";
    private final String purple = "#8B5CF6";

    public Home(javafx.stage.Stage stage) {
        root.setStyle("-fx-background-color: " + background + ";");

        root.setLeft(createSidebar());
        root.setCenter(createDashboard());
    }

    public Parent getView() {
        return root;
    }

    // One place that swaps the center area of the window.
    // Every button calls this instead of touching root directly.
    private void showScreen(Node screen) {
        root.setCenter(screen);
    }

    // Temporary screen so the buttons do something visible.
    // Replace the calls to this with the real screens later.
    private VBox createPlaceholder(String title, String message) {
        VBox content = new VBox(25);
        content.setPadding(new Insets(40));

        Label heading = new Label(title);
        heading.setTextFill(Color.WHITE);
        heading.setStyle(
            "-fx-font-size: 30px; -fx-font-weight: bold;"
        );

        Label subtitle = new Label(message);
        subtitle.setTextFill(Color.web("#A0A1B5"));

        content.getChildren().addAll(heading, subtitle);
        return content;
    }

    private void openNewGame() {
        showScreen(createPlaceholder(
            "New Game",
            "Game setup screen coming soon."
        ));
    }

    private void openResults() {
        showScreen(createPlaceholder(
            "Results",
            "Results and score history coming soon."
        ));
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(25);
        sidebar.setPadding(new Insets(35, 20, 30, 20));
        sidebar.setPrefWidth(240);
        sidebar.setStyle(
            "-fx-background-color: #17182A;"
        );

        Label logo = new Label("✦ TRIVIA NIGHT");
        logo.setTextFill(Color.WHITE);
        logo.setStyle(
            "-fx-font-size: 21px; -fx-font-weight: bold;"
        );

        Label menu = new Label("MAIN MENU");
        menu.setTextFill(Color.web("#85869B"));
        menu.setStyle("-fx-font-size: 11px;");

        Button dashboard = navButton("⌂   Dashboard", true);
        Button players = navButton("♙   Players", false);
        Button game = navButton("▶   New Game", false);
        Button results = navButton("▤   Results", false);

        dashboard.setOnAction(event ->
            showScreen(createDashboard())
        );

        players.setOnAction(event ->
            showScreen(new Players().getView())
        );

        // NEW: these two had no handler before
        game.setOnAction(event -> openNewGame());
        results.setOnAction(event -> openResults());

        sidebar.getChildren().addAll(
            logo, menu, dashboard, players, game, results
        );

        return sidebar;
    }

    private Button navButton(String text, boolean active) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPadding(new Insets(14, 15, 14, 15));

        button.setStyle(
            "-fx-background-color: " +
            (active ? purple : "transparent") +
            "; -fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-background-radius: 10;"
        );

        return button;
    }

    private VBox createDashboard() {
        VBox content = new VBox(25);
        content.setPadding(new Insets(40));

        Label heading = new Label("Dashboard");
        heading.setTextFill(Color.WHITE);
        heading.setStyle(
            "-fx-font-size: 30px; -fx-font-weight: bold;"
        );

        Label subtitle = new Label(
            "Welcome back! Here's your trivia overview."
        );
        subtitle.setTextFill(Color.web("#A0A1B5"));

        HBox statistics = new HBox(20,
            createStatCard("TOTAL PLAYERS",
                String.valueOf(getPlayerCount()),
                "Registered players"),

            createStatCard("GAMES PLAYED",
                "0",
                "Total sessions"),

            createStatCard("QUESTIONS",
                String.valueOf(getQuestionCount()),
                "Available questions"),

            createStatCard("CATEGORIES",
                String.valueOf(getCategoryCount()),
                "Available categories")
        );

        VBox activity = new VBox(12);
        activity.setPadding(new Insets(25));
        activity.setStyle(
            "-fx-background-color: " + card +
            "; -fx-background-radius: 16;"
        );

        Label activityTitle = new Label("Quick Start");
        activityTitle.setTextFill(Color.WHITE);
        activityTitle.setStyle(
            "-fx-font-size: 20px; -fx-font-weight: bold;"
        );

        Label description = new Label(
            "Ready to host your next Trivia Night?"
        );
        description.setTextFill(Color.web("#A0A1B5"));

        Button start = new Button("Start New Game  →");
        start.setPadding(new Insets(13, 22, 13, 22));
        start.setStyle(
            "-fx-background-color: " + purple +
            "; -fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 9;"
        );

        // NEW: this button had no handler before
        start.setOnAction(event -> openNewGame());

        activity.getChildren().addAll(
            activityTitle, description, start
        );

        content.getChildren().addAll(
            heading, subtitle, statistics, activity
        );

        return content;
    }

    private VBox createStatCard(
        String title, String value, String description
    ) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(22));
        box.setPrefWidth(230);
        box.setStyle(
            "-fx-background-color: " + card +
            "; -fx-background-radius: 16;"
        );

        Label titleLabel = new Label(title);
        titleLabel.setTextFill(Color.web("#A0A1B5"));
        titleLabel.setStyle("-fx-font-size: 11px;");

        Label valueLabel = new Label(value);
        valueLabel.setTextFill(Color.WHITE);
        valueLabel.setStyle(
            "-fx-font-size: 30px; -fx-font-weight: bold;"
        );

        Label descriptionLabel = new Label(description);
        descriptionLabel.setTextFill(Color.web("#85869B"));

        box.getChildren().addAll(
            titleLabel, valueLabel, descriptionLabel
        );

        return box;
    }
}
