package com.triviagame.m2;

import com.triviagame.db.QuestionDao;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.db.TimedAnswerDao;
import com.triviagame.gameplay.GameEngine;
import com.triviagame.questions.Question;
import com.triviagame.questions.QuestionMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RoundRobinGame {

    public static final int STARTING_LIVES = 3;

    private record Participant(
            SessionPlayerDao.RegisteredPlayer player,
            GameEngine engine) {
    }

    public record Turn(
            String playerName,
            int playerId,
            int turnOrder,
            int secondsPerQuestion,
            Question question,
            int score,
            int questionNumber,
            int livesRemaining) {
    }

    public record AnswerResult(
            boolean correct,
            boolean timedOut,
            int score,
            boolean finalized,
            int livesRemaining,
            boolean eliminated) {
    }

    private final int sessionId;
    private final List<Participant> participants =
            new ArrayList<>();
    private final TimedAnswerDao timings =
            new TimedAnswerDao();

    private final int[] answersPerPlayer;
    private final int[] lives;

    private int activeIndex;
    private boolean awaitingNext;
    private boolean finished;

    private int lastQuestionId;
    private int lastAnswerPlayer = -1;
    private int lastFinalAnswerLives;

    public RoundRobinGame(
            List<SessionPlayerDao.PlayerSetup> players,
            List<QuestionDao.QuestionRecord> bank,
            int questionsPerPlayer) throws SQLException {

        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException(
                    "Add at least one player."
            );
        }

        if (bank == null
                || questionsPerPlayer < 1
                || questionsPerPlayer > bank.size()) {
            throw new IllegalArgumentException(
                    "Not enough questions for this game."
            );
        }

        List<QuestionDao.QuestionRecord> shuffled =
                new ArrayList<>(bank);

        Collections.shuffle(shuffled);

        List<QuestionDao.QuestionRecord> selected =
                shuffled.subList(0, questionsPerPlayer);

        SessionPlayerDao.GameSetup setup =
                new SessionPlayerDao().createGame(players);

        sessionId = setup.sessionId();
        timings.initialize();

        for (var registered : setup.players()) {
            GameEngine engine = new GameEngine(
                    registered.playerId(),
                    sessionId
            );

            List<Question> questions =
                    new ArrayList<>();

            for (var record : selected) {
                questions.add(
                        QuestionMapper.toQuestion(record)
                );
            }

            engine.loadQuestions(questions);

            participants.add(
                    new Participant(registered, engine)
            );
        }

        answersPerPlayer = new int[participants.size()];
        lives = new int[participants.size()];

        Arrays.fill(lives, STARTING_LIVES);
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

    public int livesForPlayer(int playerIndex) {
        return lives[playerIndex];
    }

    public boolean isEliminated(int playerIndex) {
        return lives[playerIndex] <= 0;
    }

    public int survivingPlayers() {
        int count = 0;

        for (int life : lives) {
            if (life > 0) {
                count++;
            }
        }

        return count;
    }

    public Turn currentTurn() {
        if (finished) {
            throw new IllegalStateException(
                    "The game has ended."
            );
        }

        if (lives[activeIndex] <= 0) {
            throw new IllegalStateException(
                    "This player has been eliminated."
            );
        }

        Participant p = participants.get(activeIndex);

        return new Turn(
                p.player.name(),
                p.player.playerId(),
                p.player.turnOrder(),
                p.player.secondsPerQuestion(),
                p.engine.nextQuestion(),
                p.engine.getScore(),
                answersPerPlayer[activeIndex] + 1,
                lives[activeIndex]
        );
    }

    public AnswerResult answer(
            String answer,
            long elapsedMs,
            boolean timedOut) throws SQLException {

        if (finished || awaitingNext) {
            throw new IllegalStateException(
                    "No active unanswered turn."
            );
        }

        if (lives[activeIndex] <= 0) {
            throw new IllegalStateException(
                    "This player is already eliminated."
            );
        }

        Participant p = participants.get(activeIndex);
        Question question = p.engine.nextQuestion();

        if (question == null) {
            throw new IllegalStateException(
                    "No current question."
            );
        }

        boolean correct =
                !timedOut && question.checkAnswer(answer);

        int heartsBefore = lives[activeIndex];

        // Each wrong answer, including timeout, costs one heart.
        if (!correct) {
            lives[activeIndex] = Math.max(
                    0,
                    lives[activeIndex] - 1
            );
        }

        boolean eliminated = lives[activeIndex] == 0;

        // Wrong answer, still alive, still within the time limit:
        // allow another attempt without moving the question queue.
        if (!correct && !timedOut && !eliminated) {
            return new AnswerResult(
                    false,
                    false,
                    p.engine.getScore(),
                    false,
                    lives[activeIndex],
                    false
            );
        }

        // Correct, timed out, or eliminated:
        // finalize the answer and save it in the database.
        boolean savedCorrect;

        try {
            savedCorrect = p.engine.submitAnswer(
                    correct ? answer : ""
            );
        } catch (SQLException | RuntimeException ex) {
            lives[activeIndex] = heartsBefore;
            throw ex;
        }

        try {
            timings.save(
                    sessionId,
                    p.player.playerId(),
                    question.getId(),
                    Math.max(0, elapsedMs),
                    timedOut
            );

        } catch (SQLException ex) {
            try {
                p.engine.undo();
            } catch (SQLException rollbackError) {
                ex.addSuppressed(rollbackError);
            }

            lives[activeIndex] = heartsBefore;
            throw ex;
        }

        answersPerPlayer[activeIndex]++;

        lastAnswerPlayer = activeIndex;
        lastQuestionId = question.getId();
        lastFinalAnswerLives = heartsBefore;

        awaitingNext = true;

        return new AnswerResult(
                savedCorrect,
                timedOut,
                p.engine.getScore(),
                true,
                lives[activeIndex],
                eliminated
        );
    }

    // Internal undo retained for Queue/Stack coursework.
    // It is not exposed as a player-facing button.
    public boolean undoLast() throws SQLException {
        if (!awaitingNext
                || lastAnswerPlayer != activeIndex) {
            return false;
        }

        Participant p = participants.get(activeIndex);

        if (!p.engine.undo()) {
            return false;
        }

        timings.remove(
                sessionId,
                p.player.playerId(),
                lastQuestionId
        );

        answersPerPlayer[activeIndex]--;
        lives[activeIndex] = lastFinalAnswerLives;

        awaitingNext = false;
        lastQuestionId = 0;

        return true;
    }

    public boolean nextTurn() {
        if (!awaitingNext) {
            throw new IllegalStateException(
                    "Finish the current answer first."
            );
        }

        awaitingNext = false;

        // Skip every eliminated player, permanently.
        for (int step = 1;
                step <= participants.size();
                step++) {

            int candidate =
                    (activeIndex + step)
                    % participants.size();

            Participant next = participants.get(candidate);

            if (lives[candidate] > 0
                    && next.engine.hasNextQuestion()) {

                activeIndex = candidate;
                return true;
            }
        }

        // All players eliminated or finished their questions.
        finished = true;
        return false;
    }

    public List<TimedAnswerDao.Ranking> leaderboard()
            throws SQLException {
        return timings.ranking(sessionId);
    }
}
