package com.triviagame.gameplay;

import com.triviagame.model.GamePlayer;
import com.triviagame.model.questions.Question;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Coordinates players, timing, and turn order in a local game. */
public final class GameSession {
  private record Participant(GamePlayer player, GameEngine engine, List<Question> questions) {}

  public record Turn(
      String playerName,
      int playerId,
      int turnOrder,
      int secondsPerQuestion,
      Question question,
      int score,
      int questionNumber) {}

  public record AnswerResult(boolean correct, boolean timedOut, int score) {}

  private final List<Participant> participants = new ArrayList<>();
  private final GamePersistence persistence;
  private final LongSupplier clock;
  private final int[] answersPerPlayer;
  private int sessionId;
  private int activeIndex;
  private boolean awaitingNext;
  private boolean finished;
  private int lastQuestionId;
  private TurnTimer turnTimer;
  private int incorrectAttempts;
  private PendingAnswer pendingAnswer;

  /** The response and engine clock reading captured before background writes. */
  public static final class PendingAnswer {
    private final String answer;
    private final TurnTimer.Reading timing;

    private PendingAnswer(String answer, TurnTimer.Reading timing) {
      this.answer = answer;
      this.timing = timing;
    }
  }

  /** Creates a game from validated players and prepared questions. */
  public GameSession(
      List<GamePlayer> players,
      List<List<Question>> questionsByPlayer,
      GamePersistence persistence,
      LongSupplier clock) {
    if (players.isEmpty()
        || players.size() != questionsByPlayer.size()
        || questionsByPlayer.stream().anyMatch(List::isEmpty)) {
      throw new IllegalArgumentException("A game requires players and questions");
    }
    this.persistence = Objects.requireNonNull(persistence, "persistence");
    this.clock = Objects.requireNonNull(clock, "clock");
    for (int index = 0; index < players.size(); index++) {
      participants.add(participant(players.get(index), List.copyOf(questionsByPlayer.get(index))));
    }
    answersPerPlayer = new int[participants.size()];
  }

  public int sessionId() {
    return sessionId;
  }

  public boolean finished() {
    return finished;
  }

  public boolean awaitingNext() {
    return awaitingNext;
  }

  public Turn currentTurn() {
    if (finished) {
      throw new IllegalStateException("Game finished");
    }
    Participant participant = participants.get(activeIndex);
    GamePlayer player = participant.player();
    GameEngine engine = participant.engine();
    return new Turn(
        player.name(),
        player.playerId(),
        player.turnOrder(),
        player.secondsPerQuestion(),
        engine.nextQuestion(),
        engine.getScore(),
        answersPerPlayer[activeIndex] + 1);
  }

  /** Reserves the active turn and captures its authoritative submission time. */
  public synchronized PendingAnswer prepareAnswer(String answer) {
    if (finished || awaitingNext || pendingAnswer != null) {
      throw new IllegalStateException("No active unanswered turn");
    }
    startTurn();
    if (participants.get(activeIndex).engine().nextQuestion() == null) {
      throw new IllegalStateException("No question for player");
    }
    pendingAnswer = new PendingAnswer(answer, turnTimer.read());
    return pendingAnswer;
  }

  /** Saves the reserved answer from a worker thread. */
  public synchronized AnswerResult commitAnswer(PendingAnswer pending) throws SQLException {
    if (pending == null || pending != pendingAnswer) {
      throw new IllegalStateException("This submission is no longer pending");
    }
    boolean created = false;
    try {
      if (sessionId == 0) {
        createSession();
        created = true;
      }
      return saveAnswer(pending);
    } catch (SQLException | RuntimeException failure) {
      if (created) {
        discardEmptySessionAfterFailure(failure);
      }
      throw failure;
    } finally {
      pendingAnswer = null;
    }
  }

  private AnswerResult saveAnswer(PendingAnswer pending) throws SQLException {
    Participant participant = participants.get(activeIndex);
    GameEngine engine = participant.engine();
    int questionId = engine.nextQuestion().getId();
    boolean timedOut = pending.timing.timedOut();
    boolean correct = engine.submitAnswer(timedOut ? "" : pending.answer, incorrectAttempts);
    try {
      persistence.saveTiming(
          sessionId,
          participant.player().playerId(),
          questionId,
          pending.timing.elapsedMillis(),
          timedOut);
    } catch (SQLException failure) {
      try {
        engine.undo();
      } catch (SQLException rollback) {
        failure.addSuppressed(rollback);
      }
      throw failure;
    }
    answersPerPlayer[activeIndex]++;
    lastQuestionId = questionId;
    awaitingNext = true;
    return new AnswerResult(correct, timedOut, engine.getScore());
  }

  private void discardEmptySessionAfterFailure(Exception failure) {
    try {
      if (persistence.discardNewEmptySession(sessionId)) {
        sessionId = 0;
        resetEngines();
      }
    } catch (SQLException cleanup) {
      failure.addSuppressed(cleanup);
    }
  }

  /** Reserves and saves an answer on the calling thread. */
  public AnswerResult answer(String answer) throws SQLException {
    return commitAnswer(prepareAnswer(answer));
  }

  private void createSession() throws SQLException {
    sessionId = persistence.createSession(participants.stream().map(Participant::player).toList());
    resetEngines();
  }

  private void resetEngines() {
    for (int index = 0; index < participants.size(); index++) {
      Participant previous = participants.get(index);
      participants.set(index, participant(previous.player(), previous.questions()));
    }
  }

  private Participant participant(GamePlayer player, List<Question> questions) {
    GameEngine engine =
        sessionId == 0
            ? new GameEngine()
            : new GameEngine(player.playerId(), sessionId, persistence);
    engine.loadQuestions(questions);
    return new Participant(player, engine, questions);
  }

  public int remainingSeconds() {
    startTurn();
    return turnTimer.read().remainingSeconds();
  }

  /** Starts the clock once, after the player is ready. */
  public void startTurn() {
    if (finished || awaitingNext) {
      throw new IllegalStateException("No active unanswered turn");
    }
    if (turnTimer == null) {
      turnTimer = new TurnTimer(participants.get(activeIndex).player().secondsPerQuestion(), clock);
    }
  }

  /** Records an incorrect attempt before the player decides whether to use a chance. */
  public void noteIncorrectAttempt() {
    if (remainingSeconds() == 0) {
      throw new IllegalStateException("Time is up");
    }
    incorrectAttempts++;
  }

  /** Undoes the answer while its player is still on the same turn. */
  public boolean undoLast() throws SQLException {
    if (!awaitingNext) {
      return false;
    }
    Participant participant = participants.get(activeIndex);
    if (!participant.engine().undo()) {
      return false;
    }
    persistence.removeTiming(sessionId, participant.player().playerId(), lastQuestionId);
    answersPerPlayer[activeIndex]--;
    awaitingNext = false;
    lastQuestionId = 0;
    return true;
  }

  public boolean nextTurn() {
    if (!awaitingNext) {
      throw new IllegalStateException("Answer the question first");
    }
    awaitingNext = false;
    for (int offset = 1; offset <= participants.size(); offset++) {
      int candidate = (activeIndex + offset) % participants.size();
      if (participants.get(candidate).engine().hasNextQuestion()) {
        activeIndex = candidate;
        turnTimer = null;
        incorrectAttempts = 0;
        return true;
      }
    }
    finished = true;
    return false;
  }

  public List<GamePlayer.Ranking> leaderboard() throws SQLException {
    return persistence.rankings(sessionId);
  }
}
