package com.triviagame.gameplay;

import com.triviagame.model.questions.Question;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Tracks one player's question queue, points, and undo history. */
public class GameEngine {
  private static final int FULL_CREDIT = 10;
  private static final int[] RETRY_POINTS = {FULL_CREDIT, 7, 4, 2};

  /** Optional score persistence for a game backed by a saved session. */
  public interface ScoreStore {
    int addScore(
        int sessionId, int playerId, int categoryId, int questionId, boolean correct, int points)
        throws SQLException;

    boolean deleteScore(int scoreId) throws SQLException;
  }

  private final Deque<Question> questions = new ArrayDeque<>();

  private final Deque<AnswerHistory> history = new ArrayDeque<>();

  private final ScoreStore scores;

  private int score = 0;
  private int total = 0;
  private final int playerId;
  private final int sessionId;

  private record AnswerHistory(Question question, int previousScore, int savedScoreId) {}

  /** Creates an engine that keeps its history in memory. */
  public GameEngine() {
    playerId = 0;
    sessionId = 0;
    scores = null;
  }

  public GameEngine(int playerId, int sessionId, ScoreStore scores) {

    this.playerId = playerId;
    this.sessionId = sessionId;
    this.scores = Objects.requireNonNull(scores, "scores");
  }

  public void loadQuestions(List<Question> list) {

    if (list == null) {
      throw new IllegalArgumentException("Question list cannot be null");
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

  public boolean submitAnswer(String answer) throws SQLException {
    return submitAnswer(answer, 0);
  }

  /** Score a corrected answer at a reduced rate after prior incorrect attempts. */
  public boolean submitAnswer(String answer, int priorIncorrectAttempts) throws SQLException {

    if (priorIncorrectAttempts < 0) {
      throw new IllegalArgumentException("Attempt count cannot be negative");
    }

    if (questions.isEmpty()) {
      return false;
    }

    Question question = questions.peekFirst();

    boolean correct = question.checkAnswer(answer);

    // Runtime polymorphism
    int multiplier = RETRY_POINTS[Math.min(priorIncorrectAttempts, RETRY_POINTS.length - 1)];
    int points = correct ? Math.max(1, question.getPoints() * multiplier / FULL_CREDIT) : 0;

    int savedScoreId = 0;

    // Save to SQLite if this is a database game.
    if (scores != null && playerId > 0 && sessionId > 0) {

      if (question.getId() <= 0 || question.getCategoryId() <= 0) {
        throw new IllegalStateException("Question must have a database ID");
      }

      savedScoreId =
          scores.addScore(
              sessionId, playerId, question.getCategoryId(), question.getId(), correct, points);
    }

    // Only change game state after saving succeeds.
    questions.removeFirst();

    history.push(new AnswerHistory(question, score, savedScoreId));

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

      boolean deleted = scores.deleteScore(previous.savedScoreId());

      if (!deleted) {
        throw new SQLException("Unable to undo saved score");
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
