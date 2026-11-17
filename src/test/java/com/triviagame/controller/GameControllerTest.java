package com.triviagame.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.Database;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.SessionPlayerDao;
import com.triviagame.service.GameSetupService;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameControllerTest {
    @TempDir Path directory;
    private String previousPath;

    @BeforeEach
    void setUpDatabase() throws Exception {
        previousPath = System.getProperty("trivia.database.path");
        System.setProperty(
                "trivia.database.path",
                directory.resolve("controller.db").toString());
        Database.initializeSchema();
    }

    @AfterEach
    void restoreDatabasePath() {
        if (previousPath == null) {
            System.clearProperty("trivia.database.path");
        } else {
            System.setProperty("trivia.database.path", previousPath);
        }
    }

    @Test
    void controllerLoadsSetupAndCreatesSessionOnlyAfterFirstSavedAnswer()
            throws Exception {
        int categoryId = new CategoryDao().addCategory("Science");
        new QuestionDao().addQuestion(
                "What is 2 + 2?", "4", "[\"3\",\"5\"]", "easy",
                categoryId, "MULTIPLE_CHOICE");
        new PlayerDao().addPlayer("Registered player");

        GameController controller = new GameController();
        GameSetupService.Filters filters =
                new GameSetupService.Filters(categoryId, "easy", "MULTIPLE_CHOICE");

        assertEquals(1, controller.setup().players().size());
        assertEquals(1, controller.count(filters));

        var result = controller.startGame(
                List.of(new SessionPlayerDao.PlayerSetup("Guest", 30)),
                filters, 1);
        assertEquals(1, result.availableQuestions());
        var game = result.game().orElseThrow();
        assertEquals(0, game.sessionId());
        assertEquals(0, countSessions());

        assertTrue(game.answer("4").correct());
        assertTrue(game.sessionId() > 0);
        assertEquals(1, countSessions());
    }

    @Test
    void insufficientQuestionSelectionDoesNotCreateAGameSession()
            throws Exception {
        var controller = new GameController();
        var result = controller.startGame(
                List.of(new SessionPlayerDao.PlayerSetup("Guest", 30)),
                new GameSetupService.Filters(0, "any", "ANY"), 1);

        assertEquals(0, result.availableQuestions());
        assertTrue(result.game().isEmpty());
        assertEquals(0, countSessions());
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
