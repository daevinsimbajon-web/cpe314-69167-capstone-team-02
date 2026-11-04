package com.triviagame.home;

import com.triviagame.analytics.AnalyticsScreen;
import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.ScoreDao;
import com.triviagame.db.ScoreJsonImporter;
import com.triviagame.gameplay.NewGameScreen;
import com.triviagame.importer.ImportScreen;
import com.triviagame.players.Players;
import com.triviagame.results.Results;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The main window: a sidebar on the left and one screen in the center.
 * Every screen is created fresh when you open it, so it always shows
 * the latest data from the database.
 */
public class Home implements Navigator {

    // A database count that may throw
    @FunctionalInterface
    private interface SqlCount {
        int get() throws SQLException;
    }

    private final BorderPane root = new BorderPane();
    private final List<Button> navButtons = new ArrayList<>();

    private Button dashboardButton;
    private Button playersButton;
    private Button newGameButton;
    private Button resultsButton;
    private Button analyticsButton;
    private Button importButton;

    // Created the first time the Import screen opens
    private ScoreJsonImporter importer;

    public Home(Stage stage) {
        root.getStyleClass().add("app-root");
        root.setLeft(createSidebar());
        openDashboard();
    }

    public Parent getView() {
        return root;
    }

    // Called by App when the window closes
    public void shutdown() {
        if (importer != null) {
            importer.close();
        }
    }

    // ---------- Navigator ----------

    @Override
    public void show(Node screen) {
        root.setCenter(screen);
    }

    @Override
    public void openNewGame() {
        setActive(newGameButton);
        show(new NewGameScreen(this).getView());
    }

    @Override
    public void openResults() {
        setActive(resultsButton);
        show(new Results().getView());
    }

    // ---------- Screens ----------

    private void openDashboard() {
        setActive(dashboardButton);
        show(createDashboard());
    }

    private void openPlayers() {
        setActive(playersButton);
        show(new Players().getView());
    }

    private void openAnalytics() {
        setActive(analyticsButton);
        show(new AnalyticsScreen().getView());
    }

    private void openImport() {
        setActive(importButton);

        if (importer == null) {
            importer = new ScoreJsonImporter();
        }

        show(new ImportScreen(importer).getView());
    }

    // ---------- Sidebar ----------

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);

        Label logo = Ui.label("✦ TRIVIA NIGHT", "logo");
        Label menu = Ui.label("MAIN MENU", "menu-label");

        dashboardButton = navButton("⌂   Dashboard", this::openDashboard);
        playersButton = navButton("♙   Players", this::openPlayers);
        newGameButton = navButton("▶   New Game", this::openNewGame);
        resultsButton = navButton("▤   Results", this::openResults);
        analyticsButton = navButton("▥   Analytics", this::openAnalytics);
        importButton = navButton("⇩   Import Scores", this::openImport);

        sidebar.getChildren().addAll(
                logo, menu,
                dashboardButton, playersButton, newGameButton,
                resultsButton, analyticsButton, importButton
        );

        return sidebar;
    }

    private Button navButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.run());

        navButtons.add(button);
        return button;
    }

    // Highlights the button of the screen that is open
    private void setActive(Button active) {
        for (Button button : navButtons) {
            button.getStyleClass().remove("nav-button-active");
        }

        active.getStyleClass().add("nav-button-active");
    }

    // ---------- Dashboard ----------

    private VBox createDashboard() {
        VBox content = new VBox();
        content.getStyleClass().add("screen");

        Label heading = Ui.label("Dashboard", "title");
        Label subtitle = Ui.label(
                "Welcome back! Here's your trivia overview.",
                "subtitle"
        );

        HBox statistics = new HBox(20,
                statCard("TOTAL PLAYERS",
                        count(() -> new PlayerDao()
                                .getAllPlayers().size()),
                        "Registered players"),

                statCard("GAMES PLAYED",
                        count(() -> new ScoreDao()
                                .countPlayedSessions()),
                        "Sessions with saved answers"),

                statCard("QUESTIONS",
                        count(() -> new QuestionDao()
                                .getAllQuestions().size()),
                        "Available questions"),

                statCard("CATEGORIES",
                        count(() -> new CategoryDao()
                                .getAllCategories().size()),
                        "Available categories")
        );

        VBox quickStart = new VBox();
        quickStart.getStyleClass().add("card");

        Button start = Ui.button(
                "Start New Game  →",
                "primary-button",
                event -> openNewGame()
        );

        quickStart.getChildren().addAll(
                Ui.label("Quick Start", "card-title"),
                Ui.label("Ready to host your next Trivia Night?", "muted"),
                start
        );

        content.getChildren().addAll(
                heading, subtitle, statistics, quickStart
        );

        return content;
    }

    private VBox statCard(String title, String value, String description) {
        VBox box = new VBox();
        box.getStyleClass().addAll("card", "stat-card");
        box.setPrefWidth(230);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);

        box.getChildren().addAll(
                Ui.label(title, "stat-title"),
                Ui.label(value, "stat-value"),
                Ui.label(description, "stat-desc")
        );

        return box;
    }

    // Shows "?" instead of crashing if the database fails
    private String count(SqlCount query) {
        try {
            return String.valueOf(query.get());
        } catch (SQLException e) {
            return "?";
        }
    }
}
