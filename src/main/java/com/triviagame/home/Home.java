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
    private Button dashboardButton, playersButton, newGameButton;
    private Button resultsButton, analyticsButton, importButton;
    private Button fetchButton;
    private Spinner<Integer> amountSpinner;
    private ComboBox<String> difficultyBox, typeBox;
    private Label questionsCount, categoriesCount, fetchStatus;
    private FetchQuestionsTask activeFetch;
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

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);

        sidebar.getChildren().addAll(
                Ui.label("TRIVIA NIGHT", "logo"),
                Ui.label("MAIN MENU", "menu-label")
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

    private Button nav(String title, String icon, Runnable action) {
        Button button = new Button(title);
        button.setGraphic(VectorIcons.of(icon));
        button.setGraphicTextGap(14);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        button.setOnAction(event -> action.run());
        navButtons.add(button);
        return button;
    }

    private void activate(Button active) {
        for (Button button : navButtons)
            button.getStyleClass().remove("nav-button-active");
        active.getStyleClass().add("nav-button-active");
    }

    private VBox makeDashboard() {
        VBox content = new VBox();
        content.getStyleClass().add("screen");

        questionsCount = Ui.label(count(() -> new QuestionDao()
                .getAllQuestions().size()), "stat-value");
        categoriesCount = Ui.label(count(() -> new CategoryDao()
                .getAllCategories().size()), "stat-value");

        HBox statistics = new HBox(20,
                stat("TOTAL PLAYERS", Ui.label(count(() -> new PlayerDao()
                        .getAllPlayers().size()), "stat-value"), "Registered players"),
                stat("GAMES PLAYED", Ui.label(count(() -> new ScoreDao()
                        .countPlayedSessions()), "stat-value"), "Completed sessions"),
                stat("QUESTIONS", questionsCount, "Available questions"),
                stat("CATEGORIES", categoriesCount, "Available categories")
        );

        amountSpinner = new Spinner<>(1, 50, 10);
        amountSpinner.setPrefWidth(105);
        amountSpinner.setEditable(false);

        difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll("Any", "Easy", "Medium", "Hard");
        difficultyBox.getSelectionModel().selectFirst();
        difficultyBox.setPrefWidth(145);

        typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Any", "Multiple Choice", "True / False");
        typeBox.getSelectionModel().selectFirst();
        typeBox.setPrefWidth(185);

        fetchButton = Ui.button("Get More Questions", "secondary-button",
                event -> downloadQuestions());
        fetchButton.setGraphic(VectorIcons.of("download"));
        fetchButton.setGraphicTextGap(10);

        VBox bank = new VBox(14);
        bank.getStyleClass().add("card");
        fetchStatus = Ui.label("Choose the questions you want to add.", "card-note");
        fetchStatus.setWrapText(true);

        HBox filters = new HBox(12,
                amountSpinner, difficultyBox, typeBox, fetchButton);
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bank.getChildren().addAll(
                Ui.label("Question Bank", "card-title"),
                Ui.label("Find new trivia questions for your next game.", "muted"),
                Ui.label("Amount                   Difficulty                Type", "field-label"),
                filters, fetchStatus
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
                Ui.label("Ready to host your next Trivia Night?", "muted"),
                start
        );

        content.getChildren().addAll(
                Ui.label("Dashboard", "title"),
                Ui.label("Welcome back! Here's your trivia overview.", "subtitle"),
                statistics, bank, quickStart
        );
        return content;
    }

    private void downloadQuestions() {
        if (activeFetch != null && !activeFetch.isDone()) return;
        String difficulty = difficultyBox.getValue().toLowerCase(java.util.Locale.ROOT);
        String type = switch (typeBox.getValue()) {
            case "Multiple Choice" -> "multiple";
            case "True / False" -> "boolean";
            default -> "any";
        };
        FetchQuestionsTask task = new FetchQuestionsTask(
                amountSpinner.getValue(), difficulty, type);
        activeFetch = task;
        setDownloading(true);
        fetchStatus.setText("Finding new questions...");

        task.setOnSucceeded(event -> {
            setDownloading(false);
            QuestionBankService.ImportReport report = task.getValue();
            fetchStatus.setText("Added " + report.inserted()
                    + " new questions. " + report.duplicates()
                    + " were already in your collection.");
            questionsCount.setText(count(() -> new QuestionDao()
                    .getAllQuestions().size()));
            categoriesCount.setText(count(() -> new CategoryDao()
                    .getAllCategories().size()));
        });
        task.setOnFailed(event -> {
            setDownloading(false);
            fetchStatus.setText("Couldn't find questions for this selection. "
                    + "Try other filters or check your connection.");
        });
        task.setOnCancelled(event -> {
            setDownloading(false);
            fetchStatus.setText("Download cancelled.");
        });
        Thread worker = new Thread(task, "trivia-online-questions");
        worker.setDaemon(true);
        worker.start();
    }

    private void setDownloading(boolean working) {
        if (fetchButton != null) fetchButton.setDisable(working);
        if (amountSpinner != null) amountSpinner.setDisable(working);
        if (difficultyBox != null) difficultyBox.setDisable(working);
        if (typeBox != null) typeBox.setDisable(working);
    }

    private VBox stat(String title, Label number, String description) {
        VBox box = new VBox();
        box.getStyleClass().addAll("card", "stat-card");
        box.setPrefWidth(230);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.getChildren().addAll(Ui.label(title, "stat-title"),
                number, Ui.label(description, "stat-desc"));
        return box;
    }

    private String count(SqlCount query) {
        try { return String.valueOf(query.get()); }
        catch (SQLException e) { return "?"; }
    }
}