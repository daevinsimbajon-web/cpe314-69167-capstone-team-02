package com.triviagame.gameplay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;

import com.triviagame.api.QuestionParser;
import com.triviagame.controller.GameController;
import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.service.GameSetupService;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.db.TimedAnswerDao;
import com.triviagame.home.Navigator;
import com.triviagame.home.Ui;
import com.triviagame.home.VectorIcons;
import com.triviagame.questions.Question;

import javafx.animation.KeyFrame;
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
 private final GameController gameController = new GameController();
 private final ExecutorService workers;
 private long countVersion;
 private boolean loadingSetup;
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
 private final Button undoAnswer = Ui.button("Undo last answer", "secondary-button",
 e -> undoLastAnswer());
 private GameSession game;
 private Timeline countdownTimeline;
 private Timeline answerClock;
 private boolean questionVisible;
 private boolean gameBusy;
 private boolean disposed;
 private static final int STARTING_LIVES = 3;
 /** Hearts left per player id for the current game. */
 private final Map<Integer, Integer> lives = new HashMap<>();
 public NewGameScreen(Navigator navigator, ExecutorService workers) {
 this.navigator = navigator;
 this.workers = workers;
 root.getStyleClass().add("screen");
 view.setFitToWidth(true);
 view.setStyle("-fx-background: #10111F; -fx-background-color: #10111F;");
 view.sceneProperty().addListener((o, oldScene, newScene) -> {
 if (oldScene != null && newScene == null) { disposed = true; stopTimers(); }
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
 return p == null ? "" : p.name() + " (#" + p.id() + ")";
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
 timeLeft, currentScore, questionText, answers, message, new HBox(12, undoAnswer, next));
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
 loadingSetup = true;
 beginGame.setDisable(true);
 Task<GameSetupService.Setup> task = new Task<>() {
 @Override protected GameSetupService.Setup call() throws Exception { return gameController.setup(); }
 };
 task.setOnSucceeded(event -> {
 if (disposed) return;
 var setup = task.getValue();
 List<CategoryDao.CategoryRecord> list = setup.categories();
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
 registeredPlayers.getItems().addAll(setup.players());
 loadingSetup = false;
 updateAvailable();
 status.setText("Add players to begin. Each player has three optional chances.");
 });
 task.setOnFailed(event -> {
 if (disposed) return;
 loadingSetup = false;
 beginGame.setDisable(false);
 reportFailure("Could not load game setup.", task.getException());
 });
 workers.execute(task);
 }
 private GameSetupService.Filters filters() {
 CategoryChoice category = categories.getValue();
 String diff = difficulty.getValue() == null ? "Any" : difficulty.getValue();
 String type = questionType.getValue() == null ? "Any" : questionType.getValue();
 return new GameSetupService.Filters(category == null ? 0 : category.id(), diff,
 type.equals("Multiple Choice") ? "MULTIPLE_CHOICE" : type.equals("True / False") ? "TRUE_FALSE" : "ANY");
 }
 private void updateAvailable() {
 if (loadingSetup || categories.getValue() == null) return;
 var selection = filters();
 int needed = questionsPerPlayer.getValue();
 long version = ++countVersion;
 Task<Integer> task = new Task<>() {
 @Override protected Integer call() throws Exception { return gameController.count(selection); }
 };
 task.setOnSucceeded(event -> {
 if (disposed || version != countVersion) return;
 int count = task.getValue();
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
 });
 task.setOnFailed(event -> {
 if (disposed || version != countVersion) return;
 Logger.getLogger(NewGameScreen.class.getName()).log(Level.WARNING, "Question count failed", task.getException());
 available.setText("Unable to check saved questions. "
 + "Press Start Game to see the error.");
 beginGame.setDisable(false);
 });
 workers.execute(task);
 }
 private void addPlayer() {
 String entered = playerName.getText().trim();
 PlayerDao.PlayerRecord selected = registeredPlayers.getValue();
 String name = !entered.isBlank() ? entered
 : selected == null ? "" : selected.name();
 try {
 if (pending.size() >= 12)
 throw new IllegalArgumentException("Maximum of 12 players");
 Integer playerId = entered.isBlank() && selected != null ? selected.id() : null;
 if (playerId != null && pending.stream().anyMatch(p -> playerId.equals(p.playerId())))
 throw new IllegalArgumentException("This player is already added");
 pending.add(new SessionPlayerDao.PlayerSetup(name, timeLimit.getValue(), playerId));
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
 var selection = filters();
 var players = List.copyOf(pending);
 int wanted = questionsPerPlayer.getValue();
 setupCard.setDisable(true);
  Task<GameController.GameStart> task = new Task<>() {
  @Override protected GameController.GameStart call() throws Exception {
  return gameController.startGame(players, selection, wanted);
  }
 };
 task.setOnSucceeded(event -> {
 if (disposed) return;
 setupCard.setDisable(false);
  GameController.GameStart start = task.getValue();
  if (start.game().isEmpty()) {
 Ui.warning("Not enough questions saved for this selection. "
  + "Available: " + start.availableQuestions()
  + ". Needed: " + wanted + ". "
 + "Try another category, difficulty or question type, "
 + "reduce Questions per Player, or use Get More Questions "
 + "on Dashboard.");
 return;
 }
 game = start.game().orElseThrow();
 lives.clear();
 show(setupCard, false);
 show(finalCard, false);
 show(gameCard, true);
 prepareTurn();
 });
 task.setOnFailed(event -> { if (!disposed) { setupCard.setDisable(false); reportFailure("Unable to start the game.", task.getException()); } });
 workers.execute(task);
 }
 private void reportFailure(String message, Throwable error) {
 Logger.getLogger(NewGameScreen.class.getName()).log(Level.WARNING, message, error);
 Ui.error(message + " Please try again.");
 }
 /** No upcoming question is shown until the player presses Ready. */
 private void prepareTurn() {
 stopTimers();
 if (game == null || game.finished()) return;
 GameSession.Turn turn = game.currentTurn();
 show(readyPanel, true);
 show(questionPanel, false);
 readyTitle.setText(turn.playerName() + ", it's your turn!");
 readyInfo.setText("Question " + turn.questionNumber()
 + " • " + turn.secondsPerQuestion() + " seconds. "
 + "Pass the device, then press Ready.");
 bigCountdown.setText("");
 ready.setDisable(false);
 ready.setVisible(true);
 ready.setManaged(true);
 status.setText("Waiting for " + turn.playerName() + " to be ready.");
 }
 private void startCountdown() {
 if (game == null || game.awaitingNext() || countdownTimeline != null) return;
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
 GameSession.Turn turn = game.currentTurn();
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
 game.startTurn();
 timeLeft.setText("Time left: " + game.remainingSeconds() + " s");
 questionVisible = true;
 undoAnswer.setDisable(true);
 answerClock = new Timeline(new KeyFrame(Duration.millis(250), e -> {
 if (!questionVisible || game == null || game.awaitingNext()) {
 stopClock(); return;
 }
 int remaining = game.remainingSeconds();
 timeLeft.setText("Time left: " + remaining + " s");
 if (remaining == 0) submit("", null, true);
 }));
 answerClock.setCycleCount(Timeline.INDEFINITE);
 answerClock.play();
 status.setText("Good luck, " + turn.playerName() + "!");
 }
 private void submit(String option, Button clicked, boolean timedOut) {
 if (!questionVisible || game == null || game.awaitingNext() || game.finished())
 return;
 GameSession.Turn turn = game.currentTurn();
 boolean expired = timedOut || game.remainingSeconds() == 0;
 boolean right = !expired && turn.question().checkAnswer(option);
 if (!expired && !right && livesOf(turn) > 0) {
 try { game.noteIncorrectAttempt(); }
 catch (IllegalStateException expiredDuringClick) { finishAnswer("", null); return; }
 for (var node : answers.getChildren()) if (node instanceof Button button) button.setDisable(true);
 if (clicked != null) clicked.getStyleClass().add("choice-wrong");
 Button use = Ui.button("Use a chance", "primary-button", e -> {
 if (!questionVisible || game.remainingSeconds() == 0) { finishAnswer("", null); return; }
 lives.put(turn.playerId(), livesOf(turn) - 1);
 refreshHearts(livesOf(turn));
 answers.getChildren().removeIf(node -> node instanceof HBox);
 for (var node : answers.getChildren()) if (node instanceof Button button && button != clicked) button.setDisable(false);
 message.setText("Choose another answer. Time is still running.");
 });
 Button decline = Ui.button("Continue", "secondary-button", e -> finishAnswer(option, clicked));
 answers.getChildren().add(new HBox(12, use, decline));
 message.setText("Use a chance to retry, or continue with this answer.");
 return;
 }
 finishAnswer(expired ? "" : option, clicked);
 }
 private void finishAnswer(String option, Button clicked) {
 if (gameBusy || !questionVisible || game == null || game.awaitingNext() || game.finished()) return;
 GameSession.Turn turn = game.currentTurn();
 final GameSession.PendingAnswer pendingAnswer;
 try { pendingAnswer = game.prepareAnswer(option); }
 catch (IllegalStateException error) { reportFailure("Could not record the answer.", error); return; }
 gameBusy = true;
 stopClock();
 for (var node : answers.getChildren()) node.setDisable(true);
 Task<GameSession.AnswerResult> task = new Task<>() {
 @Override protected GameSession.AnswerResult call() throws Exception { return game.commitAnswer(pendingAnswer); }
 };
 task.setOnSucceeded(event -> {
 if (disposed) return;
 gameBusy = false;
 GameSession.AnswerResult result = task.getValue();
 answers.getChildren().removeIf(node -> node instanceof HBox);
 for (var node : answers.getChildren()) {
 if (!(node instanceof Button button)) continue;
 button.setDisable(true);
 if (button.getText().equalsIgnoreCase(
 QuestionParser.displayText(turn.question().getCorrectAnswer()))) {
 button.getStyleClass().add("choice-correct");
 }
 }
 currentScore.setText("Score: " + result.score());
 String correct = QuestionParser.displayText(turn.question().getCorrectAnswer());
 if (result.timedOut()) {
 message.setText("Time is up! Answer: " + correct);
 status.setText("Continue to the next player's turn.");
 } else if (result.correct()) {
 message.setText("Correct! Nicely done.");
 status.setText("Continue to the next player's turn.");
 } else {
 message.setText("Incorrect. Answer: " + correct);
 status.setText("Continue to the next player's turn.");
 }
 next.setDisable(false);
 next.setVisible(true);
 next.setManaged(true);
 undoAnswer.setDisable(false);
 });
 task.setOnFailed(event -> {
 if (disposed) return;
 gameBusy = false;
 reportFailure("Could not record the answer.", task.getException());
 if (view.getScene() != null) showQuestion();
 });
 workers.execute(task);
 }
 private void undoLastAnswer() {
 if (gameBusy || game == null || !game.awaitingNext()) return;
 gameBusy = true;
 next.setDisable(true);
 undoAnswer.setDisable(true);
 Task<Boolean> task = new Task<>() {
 @Override protected Boolean call() throws Exception { return game.undoLast(); }
 };
 task.setOnSucceeded(event -> {
 if (disposed) return;
 gameBusy = false;
 if (task.getValue()) { if (view.getScene() != null) showQuestion(); }
 else { next.setDisable(false); undoAnswer.setDisable(false); }
 });
 task.setOnFailed(event -> {
 if (disposed) return;
 gameBusy = false;
 next.setDisable(false); undoAnswer.setDisable(false);
 reportFailure("Could not undo the saved answer.", task.getException());
 });
 workers.execute(task);
 }
 private void refreshHearts(int remaining) {
 hearts.getChildren().clear();
 hearts.getChildren().add(Ui.label("Chances", "field-label"));
 for (int i = 0; i < STARTING_LIVES; i++)
 hearts.getChildren().add(VectorIcons.heart(i < remaining));
 hearts.getChildren().add(Ui.label(remaining + " remaining", "muted"));
 hearts.setAlignment(Pos.CENTER_LEFT);
 }
 private int livesOf(GameSession.Turn turn) {
 return lives.getOrDefault(turn.playerId(), STARTING_LIVES);
 }
 private void nextTurn() {
 if (gameBusy || game == null || !game.awaitingNext()) return;
 try {
 if (game.nextTurn()) prepareTurn();
 else showLeaderboard();
 } catch (Exception e) { Ui.error("Could not continue: " + e.getMessage()); }
 }
 private void showLeaderboard() {
 stopTimers();
 show(gameCard, false);
 show(finalCard, true);
 ranking.setText("Loading rankings…");
 Task<List<TimedAnswerDao.Ranking>> task = new Task<>() {
 @Override protected List<TimedAnswerDao.Ranking> call() throws Exception { return game.leaderboard(); }
 };
 task.setOnSucceeded(event -> {
 if (disposed) return;
 StringBuilder lines = new StringBuilder();
 int place = 1;
 for (var p : task.getValue()) {
 lines.append(place++).append(". ").append(p.name())
 .append(" • ").append(p.points()).append(" points")
 .append(" • ").append(String.format(Locale.ROOT, "%.2f",
 p.elapsedMillis() / 1000.0)).append(" sec\n");
 }
 ranking.setText(lines.toString());
 status.setText("Ranking: points first, then time, then turn order.");
 });
 task.setOnFailed(event -> { if (!disposed) { ranking.setText("Could not load rankings. Please try again.");
 Logger.getLogger(NewGameScreen.class.getName()).log(Level.WARNING, "Leaderboard query failed", task.getException()); } });
 workers.execute(task);
 }
 private void stopClock() {
 questionVisible = false;
 if (answerClock != null) { answerClock.stop(); answerClock = null; }
 }
 private void stopTimers() {
 stopClock();
 if (countdownTimeline != null) { countdownTimeline.stop(); countdownTimeline = null; }
 }
 private void show(VBox card, boolean visible) {
 card.setVisible(visible);
 card.setManaged(visible);
 }
}
