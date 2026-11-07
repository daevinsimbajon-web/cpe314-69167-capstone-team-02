package com.triviagame.gameplay;

import java.sql.SQLException;
import java.util.*;

import com.triviagame.questions.Question;
import com.triviagame.db.ScoreDao;

public class GameEngine {

    private final Deque<Question> questions =
            new ArrayDeque<>();

    private final Deque<AnswerHistory> history =
            new ArrayDeque<>();;

    private final ScoreDao scoreDao;
    
    private int score = 0;
    private int total = 0;

    private record AnswerHistory(
            Question question,
            int previousScore,
            int savedScoreId) {
    }

    // Constructor for normal game and unit testing
    public GameEngine() {
        this(0, 0, new ScoreDao());
    }

    // Constructor for database-connected gameplay
    public GameEngine(int playerId, int sessionId) {
        this(playerId, sessionId, new ScoreDao());
    }

    public GameEngine(
            int playerId,
            int sessionId,
            ScoreDao scoreDao) {

        this.playerId = playerId;
        this.sessionId = sessionId;
        this.scoreDao = Objects.requireNonNull(
                scoreDao,
                "ScoreDao cannot be null"
        );
    }

    public void loadQuestions(List<Question> list) {

        if (list == null) {
            throw new IllegalArgumentException(
                "Question list cannot be null"
            );
        }
        
        questions.clear();
        history.clear();

        score = 0;
        questions.addAll(list);
        total = list.size();
    }

    public boolean hasNextQuestion() {
        return !questions.isEmpty();
    }

    public Question nextQuestion() {
        return questions.peekFirst();
    }

    public boolean submitAnswer(String answer)
            throws SQLException {

        if (questions.isEmpty()) {
            return false;
        }

        Question question = questions.peekFirst();

        boolean correct = question.checkAnswer(answer);

        // Runtime polymorphism
        int points = correct ? question.getPoints() : 0;

        int savedScoreId = 0;

        // Save to SQLite if this is a database game.
        if (playerId > 0 && sessionId > 0) {

            if (question.getId() <= 0
                    || question.getCategoryId() <= 0) {
                throw new IllegalStateException(
                    "Question must have a database ID"
                );
            }

            savedScoreId = scoreDao.addScore(
                sessionId,
                playerId,
                question.getCategoryId(),
                question.getId(),
                correct,
                points
            );
        }

        // Only change game state after saving succeeds.
        questions.removeFirst();

        history.push(new AnswerHistory(
                question, score, savedScoreId
        ));
    
        score += points;

        return correct;
    }

    public boolean undo() throws SQLException {

        if (history.isEmpty()) {
            return false;
        }

        AnswerHistory previous = history.peek();

        // Remove the previously saved answer from SQLite.
        if (previous.savedScoreId() > 0) {

            boolean deleted = scoreDao.deleteScore(
                    previous.savedScoreId()
            );

            if (!deleted) {
                throw new SQLException(
                        "Unable to undo saved score"
                );
            }
        }
        
        history.pop();
        
        questions.addFirst(previous.question());

        score = previous.previousScore();

        return true;
    }

    public int getScore() {
        return score;
    }

    public int getTotal() {
        return total;
    }
 
    public int getRemaining() {
        return questions.size();
    }
}
