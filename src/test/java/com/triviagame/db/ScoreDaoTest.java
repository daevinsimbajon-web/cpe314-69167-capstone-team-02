
package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ScoreDaoTest {

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    @Test
    void testScoreCRUD() throws SQLException {

        PlayerDao playerDao = new PlayerDao();
        CategoryDao categoryDao = new CategoryDao();
        QuestionDao questionDao = new QuestionDao();
        ScoreDao scoreDao = new ScoreDao();

        // Prepare related records
        int playerId = playerDao.addPlayer(
                "Player_" + UUID.randomUUID()
        );

        int categoryId = categoryDao.addCategory(
                "Science_" + UUID.randomUUID()
        );

        int questionId = questionDao.addQuestion(
                "What is 2 + 2?",
                "4",
                "[\"3\",\"5\",\"6\"]",
                "easy",
                categoryId
        );

        int sessionId = scoreDao.createGameSession();

        int scoreId = 0;

        try {

            // INSERT
            scoreId = scoreDao.addScore(
                    sessionId,
                    playerId,
                    categoryId,
                    questionId,
                    true,
                    10
            );

            assertTrue(scoreId > 0);

            // SELECT
            ScoreDao.ScoreRecord score =
                    scoreDao.getScoreById(scoreId)
                            .orElseThrow();

            assertEquals(playerId, score.playerId());
            assertEquals(sessionId, score.sessionId());
            assertEquals(categoryId, score.categoryId());
            assertEquals(questionId, score.questionId());
            assertTrue(score.isCorrect());
            assertEquals(10, score.points());

            // Check player's scores
            int savedScoreId = scoreId;

            assertTrue(
                    scoreDao.getScoresByPlayer(playerId)
                            .stream()
                            .anyMatch(s ->
                                    s.id() == savedScoreId
                            )
            );

            // Check session scores
            assertTrue(
                    scoreDao.getScoresBySession(sessionId)
                            .stream()
                            .anyMatch(s ->
                                    s.id() == savedScoreId
                            )
            );

            // Check total points
            assertEquals(
                    10L,
                    scoreDao.getTotalPointsByPlayer(playerId)
            );

            // UPDATE
            assertTrue(
                    scoreDao.updateScore(
                            scoreId,
                            true,
                            15
                    )
            );

            ScoreDao.ScoreRecord updated =
                    scoreDao.getScoreById(scoreId)
                            .orElseThrow();

            assertEquals(15, updated.points());

            assertEquals(
                    15L,
                    scoreDao.getTotalPointsByPlayer(playerId)
            );

        } finally {

            // Clean up test records in dependency order
            if (scoreId > 0) {
                scoreDao.deleteScore(scoreId);
            }

            questionDao.deleteQuestion(questionId);
            playerDao.deletePlayer(playerId);
            categoryDao.deleteCategory(categoryId);
            scoreDao.deleteGameSession(sessionId);
        }

        if (scoreId > 0) {
            assertTrue(
                    scoreDao.getScoreById(scoreId).isEmpty()
            );
        }
    }

    @Test
    void testInvalidScoreInputs() {

        ScoreDao dao = new ScoreDao();

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addScore(
                        -1, 1, 1, 1, true, 10
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addScore(
                        1, 1, 1, 1, true, -5
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.updateScore(
                        0, true, 10
                )
        );
    }

    @Test
    void testNonexistentScore() throws SQLException {

        ScoreDao dao = new ScoreDao();

        assertTrue(
                dao.getScoreById(
                        Integer.MAX_VALUE
                ).isEmpty()
        );

        assertFalse(
                dao.deleteScore(
                        Integer.MAX_VALUE
                )
        );

        assertEquals(
                0L,
                dao.getTotalPointsByPlayer(
                        Integer.MAX_VALUE
                )
        );
    }

    @Test
    void testForeignKeyProtection() {

        ScoreDao dao = new ScoreDao();

        assertThrows(
                SQLException.class,
                () -> dao.addScore(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        true,
                        10
                )
        );
    }
}
