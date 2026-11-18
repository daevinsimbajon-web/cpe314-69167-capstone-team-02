package com.triviagame.gameplay;

import com.triviagame.dao.CategoryDao;
import com.triviagame.dao.Database;
import com.triviagame.dao.QuestionDao;
import com.triviagame.dao.PlayerDao;
import com.triviagame.dao.SessionPlayerDao;
import com.triviagame.model.GamePlayer;
import com.triviagame.service.GameSetupService;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionTimingTest {
    @TempDir Path directory;
    private String oldPath;

    @BeforeEach void database() throws Exception {
        oldPath = System.getProperty("trivia.database.path");
        System.setProperty("trivia.database.path", directory.resolve("timing.db").toString());
        Database.initializeSchema();
    }

    @AfterEach void restorePath() {
        if (oldPath == null) System.clearProperty("trivia.database.path");
        else System.setProperty("trivia.database.path", oldPath);
    }

    @Test void engineEnforcesDeadlineAndUndoDoesNotExtendIt() throws Exception {
        int category = new CategoryDao().addCategory("Timing");
        QuestionDao questions = new QuestionDao();
        int id = questions.addQuestion("A timed question", "Yes", "[\"No\"]", "easy", category);
        var bank = List.of(questions.getQuestionById(id).orElseThrow());
        var now = new AtomicLong();
        var game = new GameSetupService().prepareGame(List.of(new GamePlayer.Setup("Player one", 5),
                new GamePlayer.Setup("Player two", 5)), bank, 1, now::get);
        var firstQuestion = game.currentTurn().question();

        game.startTurn();
        now.addAndGet(TimeUnit.SECONDS.toNanos(4));
        assertEquals(1, game.remainingSeconds());
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        var expired = game.answer("Yes");
        assertTrue(expired.timedOut());
        assertFalse(expired.correct());
        assertEquals(0, expired.score());
        assertThrows(IllegalStateException.class, () -> game.answer("Yes"));
        try (Connection connection = Database.getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM timed_answers")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1), "The other player must not receive a timeout record");
        }

        assertTrue(game.undoLast());
        assertEquals(0, game.remainingSeconds(), "Undo must not restart the clock");
        assertTrue(game.answer("Yes").timedOut());
        assertTrue(game.nextTurn());
        assertEquals(firstQuestion.getId(), game.currentTurn().question().getId());
        assertNotSame(firstQuestion, game.currentTurn().question(),
                "Each player needs an independently shuffled answer order");
        assertEquals(5, game.remainingSeconds());
        assertTrue(game.answer("Yes").correct());
    }

    @Test void equalNamesRetainDistinctPlayerIdentities() throws Exception {
        PlayerDao players = new PlayerDao();
        int first = players.addPlayer("Same Name");
        int second = players.addPlayer("Same Name");
        var setup = new SessionPlayerDao().createGame(List.of(
                new GamePlayer.Setup("Same Name", 5, first),
                new GamePlayer.Setup("Same Name", 5, second),
                new GamePlayer.Setup("Same Name", 5)));
        assertEquals(first, setup.players().get(0).playerId());
        assertEquals(second, setup.players().get(1).playerId());
        assertNotEquals(first, second);
        assertNotEquals(first, setup.players().get(2).playerId());
        assertNotEquals(second, setup.players().get(2).playerId());
    }

    @Test void sessionIsCreatedOnlyWhenTheFirstAnswerIsSaved() throws Exception {
        int category = new CategoryDao().addCategory("Delayed session");
        QuestionDao questions = new QuestionDao();
        int questionId = questions.addQuestion(
                "Persist on answer", "Yes", "[\"No\"]", "easy", category);
        var bank = List.of(questions.getQuestionById(questionId).orElseThrow());
        var game = new GameSetupService().prepareGame(
                List.of(new GamePlayer.Setup("Ready player", 30)),
                bank, 1, System::nanoTime);

        assertEquals(0, game.sessionId());
        assertEquals(0, countSessions());

        assertTrue(game.answer("Yes").correct());

        assertTrue(game.sessionId() > 0);
        assertEquals(1, countSessions());
    }

    private int countSessions() throws Exception {
        try (Connection connection = Database.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT COUNT(*) FROM game_sessions")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
