package com.triviagame.gameplay;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.triviagame.api.QuestionParser;
import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionBankService;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.home.Navigator;
import com.triviagame.home.Ui;
import com.triviagame.home.VectorIcons;
import com.triviagame.m2.RoundRobinGame;
import com.triviagame.questions.Question;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.util.StringConverter;

public final class NewGameScreen {
    private record CategoryChoice(int id, String name) {
        @Override public String toString() { return name; }
    }

    private final Navigator navigator;
    private final QuestionBankService bank = new QuestionBankService();
    private final CategoryDao categoryDao = new CategoryDao();
    private final PlayerDao playerDao = new PlayerDao();
    private final List<SessionPlayerDao.PlayerSetup> pending = new ArrayList<>();
    private final VBox root = new VBox(20);
    private final ScrollPane view = new ScrollPane(root);
    private final VBox setupCard = new VBox(14);
    private final VBox gameCard = new VBox(18);
    private final VBox readyPanel = new VBox(15);
    private final VBox questionPanel = new VBox(14);
    private final VBox finalCard = new VBox(15);
    private final ComboBox<CategoryChoice> categories = new ComboBox<>();
    private final ComboBox<String> difficulty = new ComboBox<>();
    private final ComboBox<String> questionType = new ComboBox<>();
    private final Spinner<Integer> questionsPerPlayer = new Spinner<>(1, 50, 5);
    private final ComboBox<PlayerDao.PlayerRecord> registeredPlayers = new ComboBox<>();
    private final TextField playerName = new TextField();
    private final ComboBox<Integer> timeLimit = new ComboBox<>();
    private final ListView<String> playerList = new ListView<>();
    private final Label available = Ui.label("", "card-note");
    private final Label status = Ui.label("", "card-note");
    private final Label readyTitle = Ui.label("", "card-title");
    private final Label readyInfo = Ui.label("", "muted");
    private final Label bigCountdown = Ui.label("", "title");
    private final Label turnName = Ui.label("", "card-title");
    private final Label questionNumber = Ui.label("", "muted");
    private final Label questionText = Ui.label("", "question-text");
    private final Label timeLeft = Ui.label("", "card-note");
    private final Label currentScore = Ui.label("", "card-note");
    private final Label message = Ui.label("", "feedback-info");
    private final Label ranking = Ui.label("", "card-note");
    private final HBox hearts = new HBox(8);
    private final VBox answers = new VBox(10);
    private final Button beginGame = Ui.button("Start Game", "primary-button",
            e -> startGame());
    private final Button ready = Ui.button("I'm Ready", "primary-button",
            e -> startCountdown());
    private final Button next = Ui.button("Next Turn", "primary-button",
            e -> nextTurn());
    private RoundRobinGame game;
    private Timeline countdownTimeline;
    /** Short pause after an answer, then the next turn begins by itself. */
    private PauseTransition autoAdvanceDelay;
    private static final double AUTO_ADVANCE_SECONDS = 2;
    /** Only the very first turn of a game waits for the Ready button. */
    private boolean firstTurnOfGame = true;
    private Timeline answerClock;
    private long startedAt;
    private int secondsRemaining;
    private boolean questionVisible;
    private static final int STARTING_LIVES = 3;
    /** Hearts left per player id for the current game. */
    private final Map<Integer, Integer> lives = new HashMap<>();
    public NewGameScreen(Navigator navigator) {
        this.navigator = navigator;
        root.getStyleClass().add("screen");
        view.setFitToWidth(true);
        view.setStyle("-fx-background: #10111F; -fx-background-color: #10111F;");
        view.sceneProperty().addListener((o, oldScene, newScene) -> {
            if (newScene == null) stopTimers();
        });
        categories.setPrefWidth(330);
        categories.setPromptText("Choose a category");
        difficulty.getItems().addAll("Any", "Easy", "Medium", "Hard");
        difficulty.getSelectionModel().selectFirst();
        difficulty.setPrefWidth(160);
        questionType.getItems().addAll("Any", "Multiple Choice", "True / False");
        questionType.getSelectionModel().selectFirst();
        questionType.setPrefWidth(190);
        categories.setOnAction(e -> updateAvailable());
        difficulty.setOnAction(e -> updateAvailable());
        questionType.setOnAction(e -> updateAvailable());
        questionsPerPlayer.setEditable(false);
        questionsPerPlayer.setPrefWidth(120);
        questionsPerPlayer.valueProperty().addListener(
                (obs, oldValue, newValue) -> updateAvailable());
        registeredPlayers.setPromptText("Existing player (optional)");
        registeredPlayers.setPrefWidth(240);
        registeredPlayers.setConverter(new StringConverter<>() {
            @Override public String toString(PlayerDao.PlayerRecord p) {
                return p == null ? "" : p.name();
            }
            @Override public PlayerDao.PlayerRecord fromString(String text) { return null; }
        });
        playerName.setPromptText("Or enter a new player name");
        playerName.setPrefWidth(240);
        timeLimit.getItems().addAll(10, 20, 30, 40, 50, 60);
        timeLimit.getSelectionModel().select(Integer.valueOf(30));
        timeLimit.setPrefWidth(120);
        // Readable custom cells. Ctrl-click / Shift-click selects multiple rows.
        playerList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        playerList.setPrefHeight(165);
        playerList.setMaxWidth(700);
        playerList.setStyle("-fx-control-inner-background: #232540;"
                + " -fx-control-inner-background-alt: #232540;"
                + " -fx-background-color: #232540;"
                + " -fx-selection-bar: #8B5CF6;"
                + " -fx-selection-bar-non-focused: #8B5CF6;");
        playerList.setPlaceholder(Ui.label("No players added yet", "muted"));
        playerList.setCellFactory(list -> new ListCell<>() {
            {
                selectedProperty().addListener((obs, oldValue, selected) -> updateStyle());
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTextFill(Color.WHITE);
                updateStyle();
            }
            private void updateStyle() {
                setStyle("-fx-background-color: "
                        + (isSelected() ? "#8B5CF6" : "#232540")
                        + "; -fx-padding: 11;");
            }
        });
        Button add = Ui.button("Add Player", "secondary-button", e -> addPlayer());
        add.setGraphic(VectorIcons.of("users"));
        Button remove = Ui.button("Remove Selected", "secondary-button",
                e -> removeSelected());
        remove.setGraphic(VectorIcons.of("trash"));
        beginGame.setGraphic(VectorIcons.of("play"));
        ready.setGraphic(VectorIcons.of("check"));
        next.setGraphic(VectorIcons.of("play"));
        setupCard.getStyleClass().add("card");
        setupCard.setMaxWidth(870);
        setupCard.getChildren().addAll(
                Ui.label("Game Setup", "card-title"),
                Ui.label("CATEGORY", "field-label"), categories,
                Ui.label("DIFFICULTY AND QUESTION TYPE", "field-label"),
                new HBox(12, difficulty, questionType),
                Ui.label("QUESTIONS PER PLAYER", "field-label"),
                questionsPerPlayer, available,
                Ui.label("PLAYERS (UP TO 12)", "field-label"),
                new HBox(12, registeredPlayers, playerName),
                Ui.label("TIME PER QUESTION", "field-label"),
                new HBox(12, timeLimit, add, remove),
                playerList,
                Ui.label("Tip: Ctrl-click or Shift-click to select several players.", "muted"),
                beginGame);
        readyPanel.setAlignment(Pos.CENTER);
        readyInfo.setWrapText(true);
        bigCountdown.setStyle("-fx-font-size: 76px; -fx-text-fill: #A78BFA;");
        readyPanel.getChildren().addAll(readyTitle, readyInfo, bigCountdown, ready);
        questionNumber.setWrapText(true);
        turnName.setWrapText(true);
        questionText.setWrapText(true);
        message.setWrapText(true);
        questionPanel.getChildren().addAll(turnName, questionNumber, hearts,
                timeLeft, currentScore, questionText, answers, message, next);
        show(questionPanel, false);
        next.setDisable(true);
        gameCard.getStyleClass().add("card");
        gameCard.setMaxWidth(920);
        gameCard.getChildren().addAll(readyPanel, questionPanel);
        show(gameCard, false);
        Button again = Ui.button("Play Again", "primary-button",
                e -> navigator.openNewGame());
        Button results = Ui.button("View Results", "secondary-button",
                e -> navigator.openResults());
        ranking.setWrapText(true);
        finalCard.getStyleClass().add("card");
        finalCard.setMaxWidth(880);
        finalCard.getChildren().addAll(Ui.label("Final Leaderboard", "card-title"),
                ranking, new HBox(12, again, results));
        show(finalCard, false);
        status.setWrapText(true);
        root.getChildren().addAll(Ui.label("New Game", "title"),
                Ui.label("Set up your trivia night with friends.", "subtitle"),
                setupCard, gameCard, finalCard, status);
        loadSetup();
    }

    public Parent getView() { return view; }
    private void loadSetup() {
        try {
            List<CategoryDao.CategoryRecord> list = categoryDao.getAllCategories();
            Map<Integer, String> names = new HashMap<>();
            for (var c : list) names.put(c.id(), QuestionParser.displayText(c.name()));
            categories.getItems().clear();
            categories.getItems().add(new CategoryChoice(0, "All Categories"));
            for (var c : list) {
                String label = names.get(c.id());
                if (c.parentId() != null)
                    label = names.getOrDefault(c.parentId(), "Category") + " › " + label;
                categories.getItems().add(new CategoryChoice(c.id(), label));
            }
            categories.getSelectionModel().selectFirst();
            registeredPlayers.getItems().clear();
            Set<String> unique = new HashSet<>();
            for (var p : playerDao.getAllPlayers()) {
                if (unique.add(p.name().toLowerCase(Locale.ROOT)))
                    registeredPlayers.getItems().add(p);
            }
            updateAvailable();
            status.setText("Add players to begin. Each player has three hearts.");
        } catch (SQLException e) {
            beginGame.setDisable(false);
            Ui.error("Could not load setup: " + e.getMessage());
        }
    }

    private List<QuestionDao.QuestionRecord> matchingQuestions() throws SQLException {
        CategoryChoice category = categories.getValue();
        if (category == null) return List.of();
        List<QuestionDao.QuestionRecord> pool = category.id() == 0
                ? bank.getQuestions(0)
                : categoryDao.getQuestionsRecursively(category.id());
        String diff = difficulty.getValue() == null ? "Any" : difficulty.getValue();
        String type = questionType.getValue() == null ? "Any" : questionType.getValue();
        return pool.stream().filter(q ->
                (diff.equals("Any") || q.difficulty().equalsIgnoreCase(diff))
                && (type.equals("Any")
                || (type.equals("Multiple Choice") && q.questionType().equals("MULTIPLE_CHOICE"))
                || (type.equals("True / False") && q.questionType().equals("TRUE_FALSE"))))
                .toList();
    }

    private void updateAvailable() {
        try {
            int count = matchingQuestions().size();
            int needed = questionsPerPlayer.getValue();
            if (count < needed) {
                available.setText("Only " + count + " matching questions saved; "
                        + "you need " + needed + ". Choose another category, "
                        + "difficulty or question type, select fewer questions, "
                        + "or get more questions from Dashboard.");
            } else {
                available.setText(count + " matching questions available. "
                        + "You're ready to play!");
            }
            // Keep Start enabled: a disabled JavaFX button cannot show a popup.
            beginGame.setDisable(false);
        } catch (SQLException e) {
            available.setText("Unable to check saved questions. "
                    + "Press Start Game to see the error.");
            beginGame.setDisable(false);
        }
    }

    private void addPlayer() {
        String entered = playerName.getText().trim();
        PlayerDao.PlayerRecord selected = registeredPlayers.getValue();
        String name = !entered.isBlank() ? entered
                : selected == null ? "" : selected.name();
        try {
            if (pending.size() >= 12)
                throw new IllegalArgumentException("Maximum of 12 players");
            if (pending.stream().anyMatch(p -> p.name().equalsIgnoreCase(name)))
                throw new IllegalArgumentException("This player is already added");
            pending.add(new SessionPlayerDao.PlayerSetup(name, timeLimit.getValue()));
            refreshPlayers();
            playerName.clear();
            registeredPlayers.getSelectionModel().clearSelection();
            status.setText(name + " joined the game.");
        } catch (IllegalArgumentException e) { Ui.warning(e.getMessage()); }
    }

    private void removeSelected() {
        List<Integer> indices = new ArrayList<>(
                playerList.getSelectionModel().getSelectedIndices());
        if (indices.isEmpty()) {
            Ui.warning("Select one or more players to remove");
            return;
        }
        indices.sort(Collections.reverseOrder());
        for (int i : indices) if (i >= 0 && i < pending.size()) pending.remove(i);
        refreshPlayers();
        status.setText("Selected players removed from this game.");
    }

    private void refreshPlayers() {
        playerList.getItems().clear();
        for (int i = 0; i < pending.size(); i++) {
            var p = pending.get(i);
            playerList.getItems().add((i + 1) + ". " + p.name()
                    + " • " + p.secondsPerQuestion() + " sec/question");
        }
    }

    private void startGame() {
        if (pending.isEmpty()) { Ui.warning("Add at least one player"); return; }
        try {
            List<QuestionDao.QuestionRecord> available = matchingQuestions();
            int wanted = questionsPerPlayer.getValue();
            if (available.size() < wanted) {
                Ui.warning("Not enough questions saved for this selection. "
                        + "Available: " + available.size() + ". "
                        + "Needed: " + wanted + ". "
                        + "Try another category, difficulty or question type, "
                        + "reduce Questions per Player, or use Get More Questions "
                        + "on Dashboard.");
                return;
            }
            game = new RoundRobinGame(List.copyOf(pending), available, wanted);
            lives.clear();
            firstTurnOfGame = true;
            show(setupCard, false);
            show(finalCard, false);
            show(gameCard, true);
            prepareTurn();
        } catch (Exception e) { Ui.error("Unable to start: " + e.getMessage()); }
    }

    /**
     * No upcoming question is shown yet. The first turn of a game waits for
     * the Ready button; every later turn starts its countdown right away.
     */
    private void prepareTurn() {
        stopTimers();
        if (game == null || game.finished()) return;
        RoundRobinGame.Turn turn = game.currentTurn();
        try {
            // A player with no hearts left is out: their remaining turns are
            // recorded as timed-out forfeits and skipped automatically.
            while (livesOf(turn) <= 0) {
                game.answer("", (long) turn.secondsPerQuestion() * 1000, true);
                if (!game.nextTurn()) { showLeaderboard(); return; }
                turn = game.currentTurn();
            }
        } catch (Exception e) {
            Ui.error("Could not skip an eliminated player: " + e.getMessage());
            return;
        }
        show(readyPanel, true);
        show(questionPanel, false);
        readyTitle.setText(turn.playerName() + ", it's your turn!");
        String info = "Question " + turn.questionNumber()
                + " • " + turn.secondsPerQuestion() + " seconds. ";
        bigCountdown.setText("");
        if (firstTurnOfGame) {
            readyInfo.setText(info + "Pass the device, then press Ready.");
            ready.setDisable(false);
            ready.setVisible(true);
            ready.setManaged(true);
            status.setText("Waiting for " + turn.playerName() + " to be ready.");
        } else {
            readyInfo.setText(info + "Get ready! The countdown starts now.");
            ready.setDisable(true);
            ready.setVisible(false);
            ready.setManaged(false);
            status.setText("Get ready, " + turn.playerName() + "!");
            // No pause and no Ready button: the countdown starts right away.
            startCountdown();
        }
    }

    private void startCountdown() {
        if (game == null || game.awaitingNext() || countdownTimeline != null) return;
        firstTurnOfGame = false;
        ready.setDisable(true);
        ready.setManaged(false);
        ready.setVisible(false);
        bigCountdown.setText("3");
        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> bigCountdown.setText("2")),
                new KeyFrame(Duration.seconds(2), e -> bigCountdown.setText("1")),
                new KeyFrame(Duration.seconds(3), e -> bigCountdown.setText("GO!")),
                new KeyFrame(Duration.seconds(3.7), e -> {
                    countdownTimeline = null;
                    showQuestion();
                }));
        countdownTimeline.play();
    }

    private void showQuestion() {
        if (game == null || game.awaitingNext() || game.finished()) return;
        RoundRobinGame.Turn turn = game.currentTurn();
        Question question = turn.question();
        if (question == null) { Ui.error("Question unavailable"); return; }
        show(readyPanel, false);
        show(questionPanel, true);
        turnName.setText(turn.playerName());
        questionNumber.setText("Question " + turn.questionNumber()
                + " • Answer correctly before time runs out");
        currentScore.setText("Score: " + turn.score());
        refreshHearts(livesOf(turn));
        answers.getChildren().clear();
        for (String option : question.getChoices()) {
            Button answer = new Button(QuestionParser.displayText(option));
            answer.getStyleClass().add("choice-button");
            answer.setWrapText(true);
            answer.setMaxWidth(Double.MAX_VALUE);
            answer.setOnAction(e -> submit(option, answer, false));
            answers.getChildren().add(answer);
        }
        questionText.setText(QuestionParser.displayText(question.getText()));
        message.setText("");
        next.setDisable(true);
        next.setVisible(false);
        next.setManaged(false);
        secondsRemaining = turn.secondsPerQuestion();
        timeLeft.setText("Time left: " + secondsRemaining + " s");
        questionVisible = true;
        startedAt = System.nanoTime();
        answerClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (!questionVisible || game == null || game.awaitingNext()) {
                stopClock(); return;
            }
            secondsRemaining--;
            timeLeft.setText("Time left: " + Math.max(0, secondsRemaining) + " s");
            if (secondsRemaining <= 0) submit("", null, true);
        }));
        answerClock.setCycleCount(Timeline.INDEFINITE);
        answerClock.play();
        status.setText("Good luck, " + turn.playerName() + "!");
    }

    private void submit(String option, Button clicked, boolean timedOut) {
        if (!questionVisible || game == null || game.awaitingNext() || game.finished())
            return;
        RoundRobinGame.Turn turn = game.currentTurn();
        long limit = (long) turn.secondsPerQuestion() * 1000;
        long elapsed = timedOut ? limit
                : Math.min(limit, Math.max(0, (System.nanoTime() - startedAt) / 1_000_000));
        // Hearts are tracked here: a wrong pick or a timeout costs one heart.
        boolean right = !timedOut && turn.question().checkAnswer(option);
        int livesLeft = livesOf(turn) - (right ? 0 : 1);
        if (!timedOut && !right && livesLeft > 0) {
            // Wrong attempt with hearts left: nothing is saved yet, and the
            // question and its original countdown stay active.
            lives.put(turn.playerId(), livesLeft);
            refreshHearts(livesLeft);
            if (clicked != null) {
                clicked.setDisable(true);
                clicked.getStyleClass().add("choice-wrong");
            }
            message.setText("Not quite! You lost a heart. Try another answer.");
            status.setText(livesLeft + (livesLeft == 1 ? " heart" : " hearts")
                    + " remaining — time is still running.");
            return;
        }
        try {
            RoundRobinGame.AnswerResult result = game.answer(option, elapsed, timedOut);
            lives.put(turn.playerId(), livesLeft);
            refreshHearts(livesLeft);
            stopClock();
            for (var node : answers.getChildren()) {
                Button button = (Button) node;
                button.setDisable(true);
                if (button.getText().equalsIgnoreCase(
                        QuestionParser.displayText(turn.question().getCorrectAnswer()))) {
                            button.getStyleClass().add("choice-correct");
                        }
            }
            currentScore.setText("Score: " + result.score());
            String correct = QuestionParser.displayText(turn.question().getCorrectAnswer());
            if (livesLeft == 0) {
                message.setText("You lost your final heart - eliminated! "
                        + "Correct answer: " + correct);
                status.setText(turn.playerName()
                        + " is out of the game. Other players can continue.");
            } else if (timedOut) {
                message.setText("Time is up! One heart lost. Answer: " + correct);
                status.setText("Next turn starting...");
            } else if (result.correct()) {
                message.setText("Correct! Nicely done.");
                status.setText("Next turn starting...");
            } else {
                message.setText("Incorrect. Answer: " + correct);
                status.setText("Next turn starting...");
            }
            // No Next button: move on by itself after a short pause.
            autoAdvanceDelay = new PauseTransition(Duration.seconds(AUTO_ADVANCE_SECONDS));
            autoAdvanceDelay.setOnFinished(e -> {
                autoAdvanceDelay = null;
                nextTurn();
            });
            autoAdvanceDelay.play();
        } catch (Exception e) {
            Ui.error("Could not record answer: " + e.getMessage());
        }
    }

    private void refreshHearts(int remaining) {
        hearts.getChildren().clear();
        hearts.getChildren().add(Ui.label("Lives", "field-label"));
        for (int i = 0; i < STARTING_LIVES; i++)
            hearts.getChildren().add(VectorIcons.heart(i < remaining));
        hearts.getChildren().add(Ui.label(remaining + " remaining", "muted"));
        hearts.setAlignment(Pos.CENTER_LEFT);
    }

    private int livesOf(RoundRobinGame.Turn turn) {
        return lives.getOrDefault(turn.playerId(), STARTING_LIVES);
    }

    private void nextTurn() {
        if (game == null || !game.awaitingNext()) return;
        try {
            if (game.nextTurn()) prepareTurn();
            else showLeaderboard();
        } catch (Exception e) { Ui.error("Could not continue: " + e.getMessage()); }
    }

    private void showLeaderboard() {
        stopTimers();
        show(gameCard, false);
        show(finalCard, true);
        try {
            StringBuilder lines = new StringBuilder();
            int place = 1;
            for (var p : game.leaderboard()) {
                lines.append(place++).append(". ").append(p.name())
                        .append(" • ").append(p.points()).append(" points")
                        .append(" • ").append(String.format(Locale.ROOT, "%.2f",
                        p.elapsedMillis() / 1000.0)).append(" sec\n");
            }
            ranking.setText(lines.toString());
            status.setText("Ranking: points first, then time, then turn order.");
        } catch (SQLException e) {
            ranking.setText("Couldn't load rankings: " + e.getMessage());
        }
    }

    private void stopClock() {
        questionVisible = false;
        if (answerClock != null) { answerClock.stop(); answerClock = null; }
    }

    private void stopTimers() {
        stopClock();
        if (countdownTimeline != null) { countdownTimeline.stop(); countdownTimeline = null; }
        if (autoAdvanceDelay != null) { autoAdvanceDelay.stop(); autoAdvanceDelay = null; }
    }

    private void show(VBox card, boolean visible) {
        card.setVisible(visible);
        card.setManaged(visible);
    }
}
