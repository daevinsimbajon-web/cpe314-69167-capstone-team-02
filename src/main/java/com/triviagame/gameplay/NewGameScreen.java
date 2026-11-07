package com.triviagame.gameplay;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.ScoreDao;
import com.triviagame.home.Navigator;
import com.triviagame.home.Ui;
import com.triviagame.questions.Question;
import com.triviagame.questions.QuestionMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Game setup: choose a player, a category and how many questions,
 * then start. Replaces the old "coming soon" placeholder.
 */
public class NewGameScreen {

    // One entry in the category drop-down
    private record CategoryChoice(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final VBox root = new VBox();
    private final Navigator navigator;

    private final PlayerDao playerDao = new PlayerDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final ScoreDao scoreDao = new ScoreDao();

    private final ComboBox<PlayerDao.PlayerRecord> playerBox =
            new ComboBox<>();
    private final ComboBox<CategoryChoice> categoryBox =
            new ComboBox<>();
    private final Spinner<Integer> countSpinner =
            new Spinner<>(1, 6, 5);

    private final Label hint = Ui.label("", "card-note");

    private final Button startButton = Ui.button(
            "Start Game  →",
            "primary-button",
            event -> startGame()
    );

    public NewGameScreen(Navigator navigator) {
        this.navigator = navigator;

        root.getStyleClass().add("screen");

        playerBox.setPromptText("Choose a player");
        playerBox.setPrefWidth(360);
        playerBox.setConverter(
                new StringConverter<PlayerDao.PlayerRecord>() {
                    @Override
                    public String toString(PlayerDao.PlayerRecord player) {
                        return player == null ? "" : player.name();
                    }

                    @Override
                    public PlayerDao.PlayerRecord fromString(String text) {
                        return null;
                    }
                }
        );

        categoryBox.setPromptText("Choose a category");
        categoryBox.setPrefWidth(360);
        categoryBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> updateAvailable()
        );

        countSpinner.setEditable(false);
        countSpinner.setPrefWidth(120);

        VBox card = new VBox();
        card.getStyleClass().add("card");
        card.setMaxWidth(560);

        card.getChildren().addAll(
                Ui.label("Game setup", "card-title"),
                Ui.label("PLAYER", "field-label"),
                playerBox,
                Ui.label("CATEGORY", "field-label"),
                categoryBox,
                Ui.label("NUMBER OF QUESTIONS", "field-label"),
                countSpinner,
                hint,
                startButton
        );

        root.getChildren().addAll(
                Ui.label("New Game", "title"),
                Ui.label("Pick who is playing and what to play.",
                        "subtitle"),
                card
        );

        load();
    }

    public Parent getView() {
        return root;
    }

    // Fill both drop-downs from the database
    private void load() {
        try {
            List<PlayerDao.PlayerRecord> players =
                    playerDao.getAllPlayers();

            playerBox.setItems(
                    FXCollections.observableArrayList(players)
            );

            List<CategoryDao.CategoryRecord> all =
                    categoryDao.getAllCategories();

            Map<Integer, String> names = new HashMap<>();
            for (CategoryDao.CategoryRecord category : all) {
                names.put(category.id(), category.name());
            }

            List<CategoryChoice> choices = new ArrayList<>();
            for (CategoryDao.CategoryRecord category : all) {

                // Show subcategories as "Parent › Child"
                String label = category.parentId() == null
                        ? category.name()
                        : names.getOrDefault(category.parentId(), "?")
                                + " › " + category.name();

                choices.add(new CategoryChoice(category.id(), label));
            }

            categoryBox.setItems(
                    FXCollections.observableArrayList(choices)
            );

            if (!players.isEmpty()) {
                playerBox.getSelectionModel().selectFirst();
            }

            if (!choices.isEmpty()) {
                categoryBox.getSelectionModel().selectFirst();
            }

            if (players.isEmpty()) {
                hint.setText(
                        "No players yet. Add one on the Players "
                        + "screen first."
                );
                startButton.setDisable(true);
            }

        } catch (SQLException e) {
            Ui.error("Unable to load game setup: " + e.getMessage());
            startButton.setDisable(true);
        }
    }

    // Tell the user how many questions the category has
    private void updateAvailable() {
        CategoryChoice category = categoryBox.getValue();

        if (category == null) {
            return;
        }

        try {
            int available = categoryDao
                    .getQuestionsRecursively(category.id())
                    .size();

            hint.setText(
                    available == 0
                            ? "This category has no questions yet."
                            : available + " questions available "
                                    + "(including subcategories)."
            );

        } catch (SQLException e) {
            hint.setText("Could not count questions.");
        }
    }

    private void startGame() {
        PlayerDao.PlayerRecord player = playerBox.getValue();
        CategoryChoice category = categoryBox.getValue();

        if (player == null) {
            Ui.warning("Please choose a player.");
            return;
        }

        if (category == null) {
            Ui.warning("Please choose a category.");
            return;
        }

        int wanted = countSpinner.getValue();

        try {
            // Recursive retrieval: the category and all its subcategories
            List<QuestionDao.QuestionRecord> records =
                    categoryDao.getQuestionsRecursively(category.id());

            if (records.isEmpty()) {
                Ui.warning(
                        "There are no questions in this category yet."
                );
                return;
            }

            List<Question> questions = new ArrayList<>();

            for (QuestionDao.QuestionRecord record : records) {
                questions.add(QuestionMapper.toQuestion(record));
            }

            Collections.shuffle(questions);

            if (questions.size() > wanted) {
                questions = new ArrayList<>(
                        questions.subList(0, wanted)
                );
            }

            int sessionId = scoreDao.createGameSession();

            GameEngine engine = new GameEngine(player.id(), sessionId);
            engine.loadQuestions(questions);

            navigator.show(
                    new GameScreen(
                            engine,
                            player.id(),
                            player.name(),
                            sessionId,
                            navigator
                    ).getView()
            );

        } catch (SQLException | IllegalArgumentException e) {
            Ui.error("Unable to start the game: " + e.getMessage());
        }
    }
}
