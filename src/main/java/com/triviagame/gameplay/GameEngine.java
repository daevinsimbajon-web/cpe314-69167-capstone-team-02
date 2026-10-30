
package com.triviagame.gameplay;
import com.triviagame.Question;

import java.sql.SQLException;
import java.util.*;

import com.triviagame.db.ScoreDao;

public class GameEngine {

    private Queue<Question> questions = new LinkedList<>();

    private Stack<Question> history = new Stack<>();
    private Stack<Integer> scoreHistory = new Stack<>();
    private Stack<Integer> savedScoreIds = new Stack<>();

    private ScoreDao scoreDao = new ScoreDao();

    private int score = 0;
    private int playerId;
    private int sessionId;

    // Constructor for normal game and unit testing
    public GameEngine() {
        this(0, 0);
    }

    // Constructor for database-connected gameplay
    public GameEngine(int playerId, int sessionId) {
        this.playerId = playerId;
        this.sessionId = sessionId;
    }

    public void loadQuestions(List<Question> list) {
        questions.clear();
        history.clear();
        scoreHistory.clear();
        savedScoreIds.clear();

        score = 0;
        questions.addAll(list);
    }

    public boolean hasNextQuestion() {
        return !questions.isEmpty();
    }

    public Question nextQuestion() {
        return questions.peek();
    }

    public boolean submitAnswer(String answer)
            throws SQLException {

        if (questions.isEmpty()) {
            return false;
        }

        Question question = questions.peek();

        boolean correct = question.checkAnswer(answer);

        // Runtime polymorphism
        int points = correct ? question.getPoints() : 0;

        int savedId = 0;

        // Save to SQLite if this is a database game.
        if (playerId > 0 && sessionId > 0) {

            if (question.getId() <= 0
                    || question.getCategoryId() <= 0) {
                throw new IllegalStateException(
                    "Question must have a database ID"
                );
            }

            savedId = scoreDao.addScore(
                sessionId,
                playerId,
                question.getCategoryId(),
                question.getId(),
                correct,
                points
            );
        }

        // Only change game state after saving succeeds.
        questions.poll();

        history.push(question);
        scoreHistory.push(score);
        savedScoreIds.push(savedId);

        score += points;

        return correct;
    }

    public boolean undo() throws SQLException {

        if (history.isEmpty()) {
            return false;
        }

        int savedId = savedScoreIds.peek();

        // Remove the previously saved answer from SQLite.
        if (savedId > 0) {
            if (!scoreDao.deleteScore(savedId)) {
                throw new SQLException(
                    "Unable to undo saved score"
                );
            }
        }

        savedScoreIds.pop();

        Question previous = history.pop();

        score = scoreHistory.pop();

        LinkedList<Question> updated = new LinkedList<>();

        updated.add(previous);
        updated.addAll(questions);

        questions = updated;

        return true;
    }

    public int getScore() {
        return score;
    }
}
