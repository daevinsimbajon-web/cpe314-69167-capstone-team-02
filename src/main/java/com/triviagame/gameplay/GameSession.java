package com.triviagame.gameplay;

import com.triviagame.db.QuestionDao;
import com.triviagame.db.CategoryDao;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.db.TimedAnswerDao;
import com.triviagame.questions.Question;
import com.triviagame.questions.QuestionMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class GameSession {

    private record Participant(
            SessionPlayerDao.RegisteredPlayer player,
            GameEngine engine,
            List<Question> questions) {
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

    private int sessionId;

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
    private final LongSupplier clock;
    private TurnTimer turnTimer;
    private int incorrectAttempts;
    private PendingAnswer pendingAnswer;

    /** Reserved response and engine clock snapshot, created before background I/O. */
    public static final class PendingAnswer {
        private final String answer;private final TurnTimer.Reading timing;
        private PendingAnswer(String answer, TurnTimer.Reading timing) {this.answer=answer;this.timing=timing;}
    }

    public GameSession(
            List<SessionPlayerDao.PlayerSetup> players,
            List<QuestionDao.QuestionRecord> bank,
            int questionsPerPlayer)
            throws SQLException {
        this(players, bank, questionsPerPlayer, System::nanoTime);
    }

    GameSession(List<SessionPlayerDao.PlayerSetup> players,
            List<QuestionDao.QuestionRecord> bank, int questionsPerPlayer,
            LongSupplier clock) throws SQLException {
        this.clock = Objects.requireNonNull(clock, "clock");

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

        if (players == null || players.isEmpty()) throw new IllegalArgumentException("Add at least one player");
        Map<Integer, CategoryDao.CategoryRecord> categories = new HashMap<>();
        for (var category : new CategoryDao().getAllCategories()) categories.put(category.id(), category);
        List<List<Question>> prepared = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            List<Question> questions = new ArrayList<>();
            for (var record : selected) {
                List<String> ancestry = new ArrayList<>();
                var visited = new HashSet<Integer>();
                Integer category = record.categoryId();
                while (category != null && visited.add(category)) {
                    var node = categories.get(category);
                    if (node == null) break;
                    ancestry.add(node.name());
                    category = node.parentId();
                }
                questions.add(QuestionMapper.toQuestion(record, ancestry));
            }
            prepared.add(questions);
        }

        // Validate the question bank and initialize supporting tables before creating a session.
        timings.initialize();
        var registered = new SessionPlayerDao().preparePlayers(players);
        for (int i = 0; i < registered.size(); i++) {
            var player = registered.get(i);
            GameEngine engine = new GameEngine();
            engine.loadQuestions(prepared.get(i));
            participants.add(new Participant(player, engine, prepared.get(i)));
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

    /** Snapshots timing and prevents another submission while saving. */
    public synchronized PendingAnswer prepareAnswer(String answer) {
        if (finished || awaitingNext || pendingAnswer != null) throw new IllegalStateException("No active unanswered turn");
        startTurn();
        if (participants.get(activeIndex).engine.nextQuestion() == null) throw new IllegalStateException("No question for player");
        pendingAnswer = new PendingAnswer(answer, turnTimer.read());
        return pendingAnswer;
    }

    /** Performs persistence after the UI has captured the authoritative submission time. */
    public synchronized AnswerResult commitAnswer(PendingAnswer pending) throws SQLException {
        if (pending == null || pending != pendingAnswer) throw new IllegalStateException("This submission is no longer pending");
        boolean created = false;
        try {
            if (sessionId == 0) {createSession();created = true;}
            Participant p = participants.get(activeIndex);
            Question current = p.engine.nextQuestion();
            boolean correct = p.engine.submitAnswer(pending.timing.timedOut() ? "" : pending.answer, incorrectAttempts);
            try {
                timings.save(sessionId,p.player.playerId(),current.getId(),pending.timing.elapsedMillis(),pending.timing.timedOut());
            } catch (SQLException e) {
                try {p.engine.undo();} catch (SQLException rollbackError) {e.addSuppressed(rollbackError);}
                throw e;
            }
            answersPerPlayer[activeIndex]++;
            lastAnswerNumber = activeIndex;lastQuestionId = current.getId();awaitingNext = true;
            return new AnswerResult(correct,pending.timing.timedOut(),p.engine.getScore());
        } catch (SQLException | RuntimeException failure) {
            if (created) {
                try {if (new SessionPlayerDao().discardNewEmptySession(sessionId)) {sessionId=0;resetEngines();}}
                catch (SQLException cleanup) {failure.addSuppressed(cleanup);}
            }
            throw failure;
        } finally {
            pendingAnswer = null;
        }
    }

    public AnswerResult answer(String answer) throws SQLException {return commitAnswer(prepareAnswer(answer));}

    private void createSession() throws SQLException {
        sessionId = new SessionPlayerDao().createSession(participants.stream().map(Participant::player).toList());
        resetEngines();
    }

    private void resetEngines() {
        for (int i=0;i<participants.size();i++) {
            Participant prior=participants.get(i);
            GameEngine engine=sessionId==0?new GameEngine():new GameEngine(prior.player.playerId(),sessionId);
            engine.loadQuestions(prior.questions);
            participants.set(i,new Participant(prior.player,engine,prior.questions));
        }
    }

    public int remainingSeconds() {
        startTurn();
        return turnTimer.read().remainingSeconds();
    }

    /** Starts the clock once, after the player is ready. */
    public void startTurn() {
        if (finished || awaitingNext) throw new IllegalStateException("No active unanswered turn");
        if (turnTimer == null) turnTimer = new TurnTimer(participants.get(activeIndex).player.secondsPerQuestion(), clock);
    }

    /** Records an incorrect attempt before the player decides whether to use a chance. */
    public void noteIncorrectAttempt() {
        if (remainingSeconds() == 0) throw new IllegalStateException("Time is up");
        incorrectAttempts++;
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
                turnTimer = null;
                incorrectAttempts = 0;
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
