package com.triviagame.m2;

import com.triviagame.db.QuestionDao;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.db.TimedAnswerDao;
import com.triviagame.gameplay.GameEngine;
import com.triviagame.questions.Question;
import com.triviagame.questions.QuestionMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RoundRobinGame {

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
            int questionNumber) {
    }

    public record AnswerResult(
            boolean correct,
            boolean timedOut,
            int score) {
    }

    private final int sessionId;

    private final List<Participant> participants =
            new ArrayList<>();

    private final TimedAnswerDao timings =
            new TimedAnswerDao();

    private int activeIndex = 0;
    private boolean awaitingNext = false;
    private boolean finished = false;

    private int lastQuestionId = 0;
    private int lastAnswerNumber = 0;

    private int[] answersPerPlayer;

    public RoundRobinGame(
            List<SessionPlayerDao.PlayerSetup> players,
            List<QuestionDao.QuestionRecord> bank,
            int questionsPerPlayer)
            throws SQLException {

        if (bank == null ||
                questionsPerPlayer < 1 ||
                questionsPerPlayer > bank.size()) {

            throw new IllegalArgumentException(
                "Not enough bank questions for requested game length"
            );
        }

        List<QuestionDao.QuestionRecord> pool =
                new ArrayList<>(bank);

        Collections.shuffle(pool);

        List<QuestionDao.QuestionRecord> selected =
                pool.subList(0, questionsPerPlayer);

        SessionPlayerDao.GameSetup setup =
                new SessionPlayerDao().createGame(players);

        sessionId = setup.sessionId();

        timings.initialize();

        for (var player : setup.players()) {

            GameEngine engine = new GameEngine(
                player.playerId(),
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
                new Participant(player, engine)
            );
        }

        answersPerPlayer =
                new int[participants.size()];
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
            throw new IllegalStateException(
                "Game finished"
            );
        }

        Participant p =
                participants.get(activeIndex);

        return new Turn(
            p.player.name(),
            p.player.playerId(),
            p.player.turnOrder(),
            p.player.secondsPerQuestion(),
            p.engine.nextQuestion(),
            p.engine.getScore(),
            answersPerPlayer[activeIndex] + 1
        );
    }

    public AnswerResult answer(
            String answer,
            long elapsedMs,
            boolean timedOut)
            throws SQLException {

        if (finished || awaitingNext) {
            throw new IllegalStateException(
                "No active unanswered turn"
            );
        }

        Participant p =
                participants.get(activeIndex);

        Question current =
                p.engine.nextQuestion();

        if (current == null) {
            throw new IllegalStateException(
                "No question for player"
            );
        }

        boolean correct = p.engine.submitAnswer(
            timedOut ? "" : answer
        );

        try {
            timings.save(
                sessionId,
                p.player.playerId(),
                current.getId(),
                elapsedMs,
                timedOut
            );

        } catch (SQLException e) {

            // Reverse the score if timing storage fails.
            try {
                p.engine.undo();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }

            throw e;
        }

        answersPerPlayer[activeIndex]++;

        lastAnswerNumber = activeIndex;
        lastQuestionId = current.getId();

        awaitingNext = true;

        return new AnswerResult(
            correct,
            timedOut,
            p.engine.getScore()
        );
    }

    public boolean undoLast()
            throws SQLException {

        if (!awaitingNext ||
                lastAnswerNumber != activeIndex) {
            return false;
        }

        Participant p =
                participants.get(activeIndex);

        if (!p.engine.undo()) {
            return false;
        }

        timings.remove(
            sessionId,
            p.player.playerId(),
            lastQuestionId
        );

        answersPerPlayer[activeIndex]--;

        awaitingNext = false;
        lastQuestionId = 0;

        return true;
    }

    public boolean nextTurn() {

        if (!awaitingNext) {
            throw new IllegalStateException(
                "Answer the question first"
            );
        }

        awaitingNext = false;

        for (int i = 1; i <= participants.size(); i++) {

            int candidate =
                (activeIndex + i) % participants.size();

            if (participants.get(candidate)
                    .engine.hasNextQuestion()) {

                activeIndex = candidate;
                return true;
            }
        }

        finished = true;
        return false;
    }

    public List<TimedAnswerDao.Ranking> leaderboard()
            throws SQLException {

        return timings.ranking(sessionId);
    }
}
