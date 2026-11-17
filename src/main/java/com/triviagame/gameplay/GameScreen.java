package com.triviagame.gameplay;

import com.triviagame.db.ScoreDao;
import com.triviagame.home.Navigator;
import com.triviagame.home.Ui;
import com.triviagame.questions.Question;

import java.sql.SQLException;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The screen where a question is shown and answered.
 * It only displays things. All scoring, the question queue and the
 * undo stack stay inside GameEngine.
 */
public class GameScreen {

    private final VBox root = new VBox();

    private final GameEngine engine;
    private final int playerId;
    private final String playerName;
    private final int sessionId;
    private final Navigator navigator;
    private final ScoreDao scoreDao = new ScoreDao();

    private final Label progressLabel = Ui.label("", "card-note");
    private final Label scoreLabel = Ui.label("", "card-title");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label questionLabel = Ui.label("", "question-text");
    private final VBox choiceBox = new VBox(12);
    private final Label feedbackLabel = Ui.label("", "feedback-info");

    private final Button undoButton = Ui.button(
            "↶  Undo", "secondary-button", event -> undo()
    );

    private final Button nextButton = Ui.button(
            "Next question  →", "primary-button", event -> render()
    );

    // True after the current question was answered
    private boolean answered;

    public GameScreen(
            GameEngine engine,
            int playerId,
            String playerName,
            int sessionId,
            Navigator navigator
    ) {
        this.engine = engine;
        this.playerId = playerId;
        this.playerName = playerName;
        this.sessionId = sessionId;
        this.navigator = navigator;

        root.getStyleClass().add("screen");

        progressBar.setMaxWidth(Double.MAX_VALUE);
        questionLabel.setWrapText(true);
        feedbackLabel.setWrapText(true);
        setNextVisible(false);

        HBox statusRow = new HBox(
                progressLabel, Ui.spacer(), scoreLabel
        );
        statusRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(questionLabel, choiceBox, feedbackLabel);
        card.getStyleClass().add("card");

        HBox footer = new HBox(undoButton, Ui.spacer(), nextButton);
        footer.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(
                Ui.label("Playing as " + playerName, "title"),
                statusRow,
                progressBar,
                card,
                footer
        );

        render();
    }

    public Parent getView() {
        return root;
    }

    // Show the question at the front of the queue
    private void render() {
        Question question = engine.nextQuestion();

        if (question == null) {
            showSummary();
            return;
        }

        answered = false;

        int total = engine.getTotal();
        int number = total - engine.getRemaining() + 1;

        progressLabel.setText("Question " + number + " of " + total);
        progressBar.setProgress(total == 0 ? 0 : (double) (number - 1) / total);
        scoreLabel.setText("Score: " + engine.getScore());
        questionLabel.setText(question.getText());

        // One button per choice. True/False gives two buttons,
        // multiple choice gives four, with the same code.
        choiceBox.getChildren().clear();

        for (String choice : question.getChoices()) {
            Button button = new Button(choice);
            button.getStyleClass().add("choice-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(
                    event -> answer(question, choice, button)
            );

            choiceBox.getChildren().add(button);
        }

        setFeedback("", "feedback-info");
        setNextVisible(false);
    }

    private void answer(Question question, String choice, Button clicked) {
        if (answered) {
            return;
        }

        try {
            boolean correct = engine.submitAnswer(choice);
            answered = true;

            // Lock the buttons and color the right and wrong ones
            for (Node node : choiceBox.getChildren()) {
                Button button = (Button) node;
                button.setDisable(true);

                if (button.getText()
                        .equalsIgnoreCase(question.getCorrectAnswer())) {
                    button.getStyleClass().add("choice-correct");

                } else if (button == clicked) {
                    button.getStyleClass().add("choice-wrong");
                }
            }

            if (correct) {
                setFeedback(
                        "✔ Correct!  +" + question.getPoints() + " points",
                        "feedback-correct"
                );
            } else {
                setFeedback(
                        "✘ Incorrect. The answer was: "
                                + question.getCorrectAnswer(),
                        "feedback-wrong"
                );
            }

            scoreLabel.setText("Score: " + engine.getScore());

            nextButton.setText(
                    engine.hasNextQuestion()
                            ? "Next question  →"
                            : "Finish  ✓"
            );
            setNextVisible(true);

        } catch (SQLException | IllegalStateException e) {
            Ui.error("Could not save your answer: " + e.getMessage());
        }
    }

    // Undo the most recent answer. If the current question is already
    // answered, that is this answer; otherwise it is the previous one.
    private void undo() {
        try {
            if (engine.undo()) {
                render();
            } else {
                setFeedback("Nothing to undo yet.", "feedback-info");
            }

        } catch (SQLException e) {
            Ui.error("Could not undo: " + e.getMessage());
        }
    }

    // Game over: totals and where to go next
    private void showSummary() {
        int correct = -1;

        try {
            correct = (int) scoreDao.getScoresBySession(sessionId)
                    .stream()
                    .filter(s -> s.playerId() == playerId
                            && s.isCorrect())
                    .count();
        } catch (SQLException e) {
            // Leave the count unknown instead of failing the screen
        }

        VBox card = new VBox();
        card.getStyleClass().add("card");

        Button resultsButton = Ui.button(
                "View Results", "primary-button",
                event -> navigator.openResults()
        );

        Button againButton = Ui.button(
                "Play Again", "secondary-button",
                event -> navigator.openNewGame()
        );

        HBox buttons = new HBox(12, resultsButton, againButton);

        card.getChildren().addAll(
                Ui.label("Game over!", "title"),
                Ui.label("Player: " + playerName, "muted"),
                Ui.label("Final score: " + engine.getScore() + " points",
                        "card-title"),
                Ui.label(
                        "Correct answers: "
                                + (correct < 0
                                        ? "?"
                                        : correct + " of "
                                                + engine.getTotal()),
                        "muted"
                ),
                buttons
        );

        root.getChildren().setAll(card);
    }

    private void setFeedback(String text, String styleClass) {
        feedbackLabel.getStyleClass().removeAll(
                "feedback-correct", "feedback-wrong", "feedback-info"
        );
        feedbackLabel.getStyleClass().add(styleClass);
        feedbackLabel.setText(text);
    }

    private void setNextVisible(boolean visible) {
        nextButton.setVisible(visible);
        nextButton.setManaged(visible);
    }
}
