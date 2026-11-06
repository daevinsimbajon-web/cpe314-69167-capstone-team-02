package com.triviagame.m2;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.QuestionBankService;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.questions.MultipleChoiceQuestion;
import com.triviagame.tasks.FetchQuestionsTask;
import com.triviagame.tasks.JsonImportTask;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

public final class M2GameScreen {

    private record CategoryOption(
            String label, int id) {

        @Override
        public String toString() {
            return label;
        }
    }

    private final QuestionBankService bank =
            new QuestionBankService();

    private final List<SessionPlayerDao.PlayerSetup>
            pendingPlayers = new ArrayList<>();

    private final VBox root = new VBox(13);

    private final Label status =
            new Label("Ready. Offline question bank is available.");

    private final Label bankCount = new Label();

    private final Label activeTurn =
            new Label("Add players and start a game.");

    private final Label countdown =
            new Label("Timer: —");

    private final Label scoreLabel =
            new Label("");

    private final Label resultLabel =
            new Label("");

    private final VBox answers =
            new VBox(8);

    private final ComboBox<CategoryOption> categories =
            new ComboBox<>();

    private final TextField nameInput =
            new TextField();

    private final TextField secondsInput =
            new TextField("30");

    private final TextField amountInput =
            new TextField("10");

    private final Spinner<Integer> questionsInput =
            new Spinner<>(1, 50, 5);

    private final ListView<String> playerList =
            new ListView<>();

    private final Button fetchButton =
            new Button("Preload / Refresh API");

    private final Button importButton =
            new Button("Import JSON");

    private final Button startButton =
            new Button("Start Game");

    private final Button nextButton =
            new Button("Next Turn");

    private final Button undoButton =
            new Button("Admin: Undo Last Answer");

    private RoundRobinGame game;
    private Timeline clock;

    private long turnStartedNanos;
    private int remainingSeconds;

    public M2GameScreen() {

        root.setPadding(new Insets(22));

        root.setStyle(
            "-fx-background-color: #f7f7fb;"
        );

        Label heading =
                new Label("Milestone 2 — Trivia Night");

        heading.setStyle(
            "-fx-font-size: 23px;"
        );

        amountInput.setPrefWidth(70);
        amountInput.setPromptText("API amount");

        nameInput.setPromptText("Player name");

        secondsInput.setPrefWidth(75);

        questionsInput.setEditable(false);

        categories.setPrefWidth(210);

        playerList.setPrefHeight(100);
        playerList.setMaxWidth(500);

        Button addPlayer =
                new Button("Add Player");

        Button removePlayer =
                new Button("Remove Selected");

        addPlayer.setOnAction(e -> addPlayer());

        removePlayer.setOnAction(e -> {

            int selected =
                playerList.getSelectionModel()
                    .getSelectedIndex();

            if (selected >= 0) {
                pendingPlayers.remove(selected);
                updatePlayerList();
            }
        });

        fetchButton.setOnAction(
            e -> fetchApi(false)
        );

        importButton.setOnAction(
            e -> selectJsonFile()
        );

        startButton.setOnAction(
            e -> startGame()
        );

        nextButton.setOnAction(
            e -> showNextTurn()
        );

        undoButton.setOnAction(
            e -> undoAnswer()
        );

        nextButton.setDisable(true);
        undoButton.setDisable(true);

        root.getChildren().addAll(
            heading,

            new Label(
                "Offline questions are seeded automatically; "
                + "fetched API questions stay in SQLite for later games."
            ),

            new Label(
                "Question source: Open Trivia Database (CC BY-SA 4.0)."
            ),

            new HBox(
                10,
                new Label("Fetch amount:"),
                amountInput,
                fetchButton,
                importButton
            ),

            bankCount,

            new HBox(
                10,
                new Label("Category:"),
                categories,
                new Label("Questions per player:"),
                questionsInput
            ),

            new HBox(
                10,
                nameInput,
                new Label("Seconds per question:"),
                secondsInput,
                addPlayer,
                removePlayer
            ),

            new Label(
                "Players (in original turn order):"
            ),

            playerList,
            startButton,
            activeTurn,
            countdown,
            answers,
            resultLabel,

            new HBox(
                10, undoButton, nextButton
            ),

            scoreLabel,
            status
        );

        refreshBank();

        // First successful launch preloads real API data.
        // If offline, the existing question bank remains usable.
        Platform.runLater(() -> {
            try {
                if (!bank.apiPreloaded()) {
                    fetchApi(true);
                }
            } catch (SQLException ex) {
                showError(
                    "Could not check API preload status", ex
                );
            }
        });
    }

    public Parent getView() {

    ScrollPane pane = new ScrollPane(root);

    pane.setFitToWidth(true);

    // Stop the question timer when leaving
    // the M2 screen through Home navigation.
    pane.sceneProperty().addListener(
        (observable, oldScene, newScene) -> {
            if (newScene == null) {
                stopClock();
            }
        }
    );

    return pane;
}

    private void addPlayer() {

        try {
            var player =
                new SessionPlayerDao.PlayerSetup(
                    nameInput.getText(),
                    Integer.parseInt(
                        secondsInput.getText().trim()
                    )
                );

            if (pendingPlayers.stream()
                    .anyMatch(p ->
                        p.name().equalsIgnoreCase(
                            player.name()
                        ))) {

                throw new IllegalArgumentException(
                    "Player name already added"
                );
            }

            if (pendingPlayers.size() >= 12) {
                throw new IllegalArgumentException(
                    "Maximum 12 players"
                );
            }

            pendingPlayers.add(player);

            updatePlayerList();

            nameInput.clear();

            status.setText(
                "Player added. Each player has their own question timer."
            );

        } catch (IllegalArgumentException ex) {
            showError(
                "Invalid player or seconds", ex
            );
        }
    }

    private void updatePlayerList() {

        playerList.getItems().clear();

        for (int i = 0; i < pendingPlayers.size(); i++) {

            var p = pendingPlayers.get(i);

            playerList.getItems().add(
                (i + 1) + ". "
                + p.name() + " — "
                + p.secondsPerQuestion()
                + " sec/question"
            );
        }
    }

    private void refreshBank() {

        try {
            bankCount.setText(
                "Saved question bank: "
                + bank.count() + " questions"
            );

            CategoryOption previous =
                    categories.getValue();

            categories.getItems().clear();

            categories.getItems().add(
                new CategoryOption(
                    "All categories", 0
                )
            );

            for (var c :
                    new CategoryDao().getAllCategories()) {

                categories.getItems().add(
                    new CategoryOption(
                        c.name(), c.id()
                    )
                );
            }

            int selectedId =
                    previous == null ? 0 : previous.id();

            categories.getSelectionModel().select(0);

            for (CategoryOption option :
                    categories.getItems()) {

                if (option.id() == selectedId) {
                    categories.setValue(option);
                    break;
                }
            }

        } catch (SQLException ex) {
            showError(
                "Cannot read question bank", ex
            );
        }
    }

    private void fetchApi(boolean automatic) {

        try {
            int amount = automatic
                ? 10
                : Integer.parseInt(
                    amountInput.getText().trim()
                );

            if (amount < 1 || amount > 50) {
                throw new IllegalArgumentException(
                    "Amount must be 1–50"
                );
            }

            runImportTask(
                new FetchQuestionsTask(amount),
                automatic
                    ? "Automatic API preload"
                    : "API refresh"
            );

        } catch (IllegalArgumentException e) {
            showError(
                "Invalid API amount", e
            );
        }
    }

    private void selectJsonFile() {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
            "Import trivia questions in JSON format"
        );

        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                "JSON files", "*.json"
            )
        );

        File file = chooser.showOpenDialog(
            root.getScene().getWindow()
        );

        if (file != null) {
            runImportTask(
                new JsonImportTask(
                    Path.of(file.toURI())
                ),
                "JSON import"
            );
        }
    }

    private void runImportTask(
            Task<QuestionBankService.ImportReport> task,
            String description) {

        fetchButton.setDisable(true);
        importButton.setDisable(true);

        status.textProperty().unbind();

        status.textProperty().bind(
            task.messageProperty()
        );

        task.setOnSucceeded(e -> {

            status.textProperty().unbind();

            fetchButton.setDisable(false);
            importButton.setDisable(false);

            var result = task.getValue();

            status.setText(
                description + ": added "
                + result.inserted() + " / "
                + result.received()
                + " (duplicates "
                + result.duplicates() + ")"
            );

            refreshBank();
        });

        task.setOnFailed(e -> {

            status.textProperty().unbind();

            fetchButton.setDisable(false);
            importButton.setDisable(false);

            showError(
                description
                + " failed (offline seed is still available)",
                task.getException()
            );
        });

        Thread worker =
                new Thread(
                    task,
                    "trivia-question-import"
                );

        worker.setDaemon(true);
        worker.start();
    }

    private void startGame() {

        stopClock();

        try {
            if (pendingPlayers.isEmpty()) {
                throw new IllegalArgumentException(
                    "Add at least one player"
                );
            }

            CategoryOption choice =
                    categories.getValue();

            int categoryId =
                    choice == null ? 0 : choice.id();

            int requested =
                    questionsInput.getValue();

            game = new RoundRobinGame(
                List.copyOf(pendingPlayers),
                bank.getQuestions(categoryId),
                requested
            );

            resultLabel.setText("");

            startButton.setText(
                "Restart with New Session"
            );

            status.setText(
                "Session " + game.sessionId()
                + " started. Each player answers "
                + "the same question set."
            );

            showCurrentTurn();

        } catch (Exception ex) {
            showError(
                "Could not start game", ex
            );
        }
    }

    private void showCurrentTurn() {

        stopClock();

        if (game == null || game.finished()) {
            return;
        }

        var turn = game.currentTurn();

        activeTurn.setText(
            "Player: " + turn.playerName()
            + " (turn " + turn.turnOrder() + ")"
            + " — Question " + turn.questionNumber()
        );

        scoreLabel.setText(
            "Current player score: " + turn.score()
        );

        answers.getChildren().clear();

        Label questionText =
                new Label(
                    turn.question().getText()
                );

        questionText.setWrapText(true);

        answers.getChildren().add(
            questionText
        );

        if (turn.question()
                instanceof MultipleChoiceQuestion multiple) {

            String[] options = multiple.getChoices().toArray(new String[0]);

            for (int i = 0; i < options.length; i++) {

                String index =
                        Integer.toString(i + 1);

                Button option =
                    new Button(
                        index + ". " + options[i]
                    );

                option.setWrapText(true);

                option.setMaxWidth(
                    Double.MAX_VALUE
                );

                option.setOnAction(
                    e -> submit(index, false)
                );

                answers.getChildren().add(option);
            }

        } else {

            Button trueButton =
                    new Button("True");

            Button falseButton =
                    new Button("False");

            trueButton.setOnAction(
                e -> submit("True", false)
            );

            falseButton.setOnAction(
                e -> submit("False", false)
            );

            answers.getChildren().add(
                new HBox(
                    10,
                    trueButton,
                    falseButton
                )
            );
        }

        undoButton.setDisable(true);
        nextButton.setDisable(true);

        remainingSeconds =
                turn.secondsPerQuestion();

        countdown.setText(
            "Time left for " + turn.playerName()
            + ": " + remainingSeconds + " sec"
        );

        turnStartedNanos =
                System.nanoTime();

        clock = new Timeline(
            new KeyFrame(
                Duration.seconds(1),
                e -> {

                    if (game == null ||
                            game.awaitingNext()) {

                        stopClock();
                        return;
                    }

                    remainingSeconds--;

                    countdown.setText(
                        "Time left: "
                        + Math.max(0, remainingSeconds)
                        + " sec"
                    );

                    if (remainingSeconds <= 0) {
                        submit("", true);
                    }
                }
            )
        );

        clock.setCycleCount(
            Timeline.INDEFINITE
        );

        clock.play();
    }

    private void submit(
            String answer,
            boolean timedOut) {

        if (game == null ||
                game.awaitingNext() ||
                game.finished()) {
            return;
        }

        int limitMs =
                game.currentTurn()
                    .secondsPerQuestion() * 1000;

        long actualMs =
                (System.nanoTime() - turnStartedNanos)
                / 1_000_000;

        long elapsed = timedOut
            ? limitMs
            : Math.min(
                limitMs,
                Math.max(0, actualMs)
            );

        stopClock();

        try {
            var result = game.answer(
                answer,
                elapsed,
                timedOut
            );

            for (var node :
                    answers.getChildren()) {

                if (node instanceof Button b) {
                    b.setDisable(true);
                }

                if (node instanceof HBox h) {

                    for (var child :
                            h.getChildren()) {

                        if (child instanceof Button b) {
                            b.setDisable(true);
                        }
                    }
                }
            }

            resultLabel.setText(
                timedOut
                    ? "Time is up — recorded as incorrect (0 points)."
                    : result.correct()
                        ? "Correct!"
                        : "Incorrect."
            );

            scoreLabel.setText(
                "Player score: " + result.score()
            );

            nextButton.setDisable(false);
            undoButton.setDisable(false);

        } catch (Exception ex) {

            showError(
                "Could not save answer", ex
            );

            if (game != null &&
                    !game.awaitingNext()) {

                showCurrentTurn();
            }
        }
    }

    private void undoAnswer() {

        if (game == null) {
            return;
        }

        try {
            if (game.undoLast()) {

                resultLabel.setText(
                    "Admin correction: last score removed; "
                    + "question reopened."
                );

                showCurrentTurn();
            }

        } catch (SQLException ex) {
            showError(
                "Could not undo saved score", ex
            );
        }
    }

    private void showNextTurn() {

        if (game == null ||
                !game.awaitingNext()) {
            return;
        }

        try {
            if (game.nextTurn()) {

                resultLabel.setText("");
                showCurrentTurn();

            } else {

                stopClock();

                answers.getChildren().clear();

                activeTurn.setText(
                    "Game over — session "
                    + game.sessionId()
                );

                countdown.setText(
                    "Timer finished"
                );

                undoButton.setDisable(true);
                nextButton.setDisable(true);

                StringBuilder lines =
                    new StringBuilder(
                        "FINAL LEADERBOARD\n"
                    );

                int rank = 1;

                for (var p : game.leaderboard()) {

                    lines.append(rank++)
                        .append(". ")
                        .append(p.name())
                        .append(" — ")
                        .append(p.points())
                        .append(" points, ")
                        .append(
                            String.format(
                                "%.2f",
                                p.elapsedMillis() / 1000.0
                            )
                        )
                        .append(" sec total, original turn ")
                        .append(p.turnOrder())
                        .append("\n");
                }

                resultLabel.setText(
                    lines.toString()
                );

                status.setText(
                    "Ranking: score DESC → elapsed time ASC "
                    + "→ original turn order ASC."
                );
            }

        } catch (SQLException ex) {
            showError(
                "Could not load leaderboard", ex
            );
        }
    }

    private void stopClock() {

        if (clock != null) {
            clock.stop();
            clock = null;
        }
    }

    private void showError(
            String prefix,
            Throwable error) {

        status.textProperty().unbind();

        status.setText(
            prefix + ": "
            + (error == null
                ? "unknown error"
                : error.getMessage())
        );
    }
}