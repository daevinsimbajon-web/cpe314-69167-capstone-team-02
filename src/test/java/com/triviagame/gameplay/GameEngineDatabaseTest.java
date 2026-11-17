package com.triviagame.gameplay;

import static org.junit.jupiter.api.Assertions.*;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.Database;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.ScoreDao;
import com.triviagame.questions.Question;
import com.triviagame.questions.QuestionMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plays whole games against the real SQLite database: answers are
 * saved as scores, Undo removes them again, and the engine refuses to
 * save a question that has no database ID.
 */
public class GameEngineDatabaseTest {

    private final PlayerDao playerDao = new PlayerDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final ScoreDao scoreDao = new ScoreDao();

    private int playerId;
    private int categoryId;
    private int sessionId;
    private final List<Integer> questionIds = new ArrayList<>();
    private List<Question> questions;

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    // A game of 3 questions: the correct answer is always "Yes"
    @BeforeEach
    void setUp() throws SQLException {

        String suffix = UUID.randomUUID().toString();

        playerId = playerDao.addPlayer("EngineTest_" + suffix);
        categoryId = categoryDao.addCategory("EngineTest_" + suffix);
        sessionId = scoreDao.createGameSession();

        questions = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            int id = questionDao.addQuestion(
                    "Engine question " + i + " " + suffix,
                    "Yes", "[\"No\",\"Maybe\"]", "easy", categoryId
            );

            questionIds.add(id);

            questions.add(QuestionMapper.toQuestion(
                    questionDao.getQuestionById(id).orElseThrow()
            ));
        }
    }

    @AfterEach
    void cleanUp() throws SQLException {

        for (ScoreDao.ScoreRecord score
                : scoreDao.getScoresByPlayer(playerId)) {
            scoreDao.deleteScore(score.id());
        }
        for (int id : questionIds) {
            questionDao.deleteQuestion(id);
        }
        questionIds.clear();

        scoreDao.deleteGameSession(sessionId);
        playerDao.deletePlayer(playerId);
        categoryDao.deleteCategory(categoryId);
    }

    private GameEngine newGame() {
        GameEngine engine = new GameEngine(playerId, sessionId);
        engine.loadQuestions(questions);
        return engine;
    }

    @Test
    void testEveryAnswerIsSavedToTheDatabase() throws SQLException {

        GameEngine engine = newGame();

        assertTrue(engine.submitAnswer("Yes"));
        assertFalse(engine.submitAnswer("No"));
        assertTrue(engine.submitAnswer("Yes"));

        List<ScoreDao.ScoreRecord> saved =
                scoreDao.getScoresBySession(sessionId);

        assertEquals(3, saved.size());
        assertEquals(20, scoreDao.getTotalPointsByPlayer(playerId));
        assertEquals(20, engine.getScore());
        assertFalse(engine.hasNextQuestion());

        for (ScoreDao.ScoreRecord score : saved) {
            assertEquals(playerId, score.playerId());
            assertEquals(categoryId, score.categoryId());
        }
    }

    @Test
    void testUndoDeletesTheSavedScore() throws SQLException {

        GameEngine engine = newGame();

        engine.submitAnswer("Yes");
        engine.submitAnswer("Yes");

        assertEquals(2, scoreDao.getScoresBySession(sessionId).size());

        assertTrue(engine.undo());

        assertEquals(1, scoreDao.getScoresBySession(sessionId).size());
        assertEquals(10, scoreDao.getTotalPointsByPlayer(playerId));
        assertEquals(10, engine.getScore());
        assertEquals(2, engine.getRemaining());
    }

    // Undo, then answer the same question again: it is saved once
    @Test
    void testUndoThenReanswerKeepsOneScore() throws SQLException {

        GameEngine engine = newGame();

        engine.submitAnswer("No");
        engine.undo();

        assertEquals(0, scoreDao.getScoresBySession(sessionId).size());

        assertTrue(engine.submitAnswer("Yes"));

        assertEquals(1, scoreDao.getScoresBySession(sessionId).size());
        assertEquals(10, scoreDao.getTotalPointsByPlayer(playerId));
    }

    // EDGE CASE: undo before any answer
    @Test
    void testUndoWithNothingToUndo() throws SQLException {

        GameEngine engine = newGame();

        assertFalse(engine.undo());
        assertEquals(0, scoreDao.getScoresBySession(sessionId).size());
    }

    // EDGE CASE: answering after the last question
    @Test
    void testAnsweringAfterTheEndSavesNothing() throws SQLException {

        GameEngine engine = newGame();

        engine.submitAnswer("Yes");
        engine.submitAnswer("Yes");
        engine.submitAnswer("Yes");

        assertFalse(engine.submitAnswer("Yes"));
        assertEquals(3, scoreDao.getScoresBySession(sessionId).size());
    }

    // EDGE CASE: a question without a database ID cannot be saved
    @Test
    void testQuestionWithoutDatabaseIdIsRejected() throws SQLException {

        GameEngine engine = new GameEngine(playerId, sessionId);

        engine.loadQuestions(List.of(
                new com.triviagame.questions.TrueFalseQuestion(
                        "No id yet", "True"
                )
        ));

        assertThrows(IllegalStateException.class,
                () -> engine.submitAnswer("True"));

        // Nothing was saved and the question is still waiting
        assertEquals(0, scoreDao.getScoresBySession(sessionId).size());
        assertEquals(1, engine.getRemaining());
    }

    // A game for player 0 is an offline practice game: nothing saved
    @Test
    void testPracticeGameDoesNotTouchTheDatabase() throws SQLException {

        GameEngine practice = new GameEngine();
        practice.loadQuestions(questions);

        practice.submitAnswer("Yes");

        assertEquals(10, practice.getScore());
        assertEquals(0, scoreDao.getScoresBySession(sessionId).size());
    }
}
