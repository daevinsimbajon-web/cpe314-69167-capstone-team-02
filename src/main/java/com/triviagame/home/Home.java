package com.triviagame.home;

import com.triviagame.analytics.AnalyticsScreen;
import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.QuestionBankService;
import com.triviagame.db.ScoreDao;
import com.triviagame.db.ScoreJsonImporter;
import com.triviagame.gameplay.NewGameScreen;
import com.triviagame.importer.ImportScreen;
import com.triviagame.m2.M2GameScreen;
import com.triviagame.players.Players;
import com.triviagame.results.Results;
import com.triviagame.tasks.FetchQuestionsTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Home implements Navigator {

    @FunctionalInterface
    private interface SqlCount { int get() throws SQLException; }

    private final BorderPane root = new BorderPane();
    private final List<Button> navButtons = new ArrayList<>();

    private Button dashboardButton;
    private Button playersButton;
    private Button newGameButton;
    private Button milestone2Button;
    private Button resultsButton;
    private Button analyticsButton;
    private Button importButton;

    private ScoreJsonImporter importer;

    public Home(Stage stage) {
        root.getStyleClass().add("app-root");
        root.setLeft(createSidebar());
        openDashboard();
    }

    public Parent getView() { return root; }

    public void shutdown() {
        if (activeFetch != null && !activeFetch.isDone()) activeFetch.cancel();
        if (importer != null) importer.close();
    }

    @Override
    public void show(Node screen) { root.setCenter(screen); }

    @Override
    public void openNewGame() {
        activate(newGameButton);
        show(new NewGameScreen(this).getView());
    }

    @Override
    public void openResults() {
        activate(resultsButton);
        show(new Results().getView());
    }

    private void openDashboard() {
        activate(dashboardButton);
        show(makeDashboard());
    }

    private void openPlayers() {
        activate(playersButton);
        show(new Players().getView());
    }

    private void openAnalytics() {
        activate(analyticsButton);
        show(new AnalyticsScreen().getView());
    }

    private void openImport() {
        activate(importButton);
        if (importer == null) importer = new ScoreJsonImporter();
        show(new ImportScreen(importer).getView());
    }

    // Open existing Milestone 2 multiplayer and API screen.
    private void openMilestone2() {
        setActive(milestone2Button);
        show(new M2GameScreen().getView());
    }

    // ---------- Sidebar ----------

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);

        Label logo = Ui.label("✦ TRIVIA NIGHT", "logo");
        Label menu = Ui.label("MAIN MENU", "menu-label");

        dashboardButton = navButton(
            "⌂   Dashboard",
            this::openDashboard
        );

        playersButton = navButton(
            "♙   Players",
            this::openPlayers
        );

        newGameButton = navButton(
            "▶   New Game",
            this::openNewGame
        );

        milestone2Button = navButton(
            "★   Multiplayer + API",
            this::openMilestone2
        );

        resultsButton = navButton(
            "▤   Results",
            this::openResults
        );

        analyticsButton = navButton(
            "▥   Analytics",
            this::openAnalytics
        );

        importButton = navButton(
            "⇩   Import Scores",
            this::openImport
        );

        sidebar.getChildren().addAll(
            logo,
            menu,
            dashboardButton,
            playersButton,
            newGameButton,
            milestone2Button,
            resultsButton,
            analyticsButton,
            importButton
        );
        dashboardButton = nav("Dashboard", "home", this::openDashboard);
        playersButton = nav("Players", "users", this::openPlayers);
        newGameButton = nav("New Game", "play", this::openNewGame);
        resultsButton = nav("Results", "list", this::openResults);
        analyticsButton = nav("Analytics", "chart", this::openAnalytics);
        importButton = nav("Import Scores", "download", this::openImport);
        sidebar.getChildren().addAll(dashboardButton, playersButton,
                newGameButton, resultsButton, analyticsButton, importButton);
        return sidebar;
    }

    private Button navButton(String text, Runnable action) {
        Button button = new Button(text);

        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        button.setOnAction(event -> action.run());
        navButtons.add(button);
        return button;
    }

    private void setActive(Button active) {
        for (Button button : navButtons) {
            button.getStyleClass().remove(
                "nav-button-active"
            );
        }

        active.getStyleClass().add(
            "nav-button-active"
        );
    }

    private VBox makeDashboard() {
        VBox content = new VBox();
        content.getStyleClass().add("screen");

        Label heading = Ui.label(
            "Dashboard",
            "title"
        );

        Label subtitle = Ui.label(
            "Welcome back! Here's your trivia overview.",
            "subtitle"
        );

        HBox statistics = new HBox(
            20,

            statCard(
                "TOTAL PLAYERS",
                count(() -> new PlayerDao()
                    .getAllPlayers().size()),
                "Registered players"
            ),

            statCard(
                "GAMES PLAYED",
                count(() -> new ScoreDao()
                    .countPlayedSessions()),
                "Sessions with saved answers"
            ),

            statCard(
                "QUESTIONS",
                count(() -> new QuestionDao()
                    .getAllQuestions().size()),
                "Available questions"
            ),

            statCard(
                "CATEGORIES",
                count(() -> new CategoryDao()
                    .getAllCategories().size()),
                "Available categories"
            )
        );

        VBox quickStart = new VBox();
        quickStart.getStyleClass().add("card");

        Button start = Ui.button(
            "Start New Game  →",
            "primary-button",
            event -> openNewGame()
        );

        if (activeFetch != null && !activeFetch.isDone()) {
            setDownloading(true);
            fetchStatus.setText("Finding new questions...");
        }

        VBox quickStart = new VBox(14);
        quickStart.getStyleClass().add("card");
        Button start = Ui.button("Start New Game", "primary-button",
                event -> openNewGame());
        start.setGraphic(VectorIcons.of("play"));
        quickStart.getChildren().addAll(
            Ui.label("Quick Start", "card-title"),
            Ui.label(
                "Ready to host your next Trivia Night?",
                "muted"
            ),
            start
        );

        content.getChildren().addAll(
            heading,
            subtitle,
            statistics,
            quickStart
        );
        return content;
    }

    private VBox statCard(
            String title,
            String value,
            String description) {

        VBox box = new VBox();
        box.getStyleClass().addAll(
            "card",
            "stat-card"
        );

        box.setPrefWidth(230);
        box.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(
            box,
            Priority.ALWAYS
        );

        box.getChildren().addAll(
            Ui.label(title, "stat-title"),
            Ui.label(value, "stat-value"),
            Ui.label(description, "stat-desc")
        );

        return box;
    }

    private String count(SqlCount query) {
        try { return String.valueOf(query.get()); }
        catch (SQLException e) { return "?"; }
    }
}