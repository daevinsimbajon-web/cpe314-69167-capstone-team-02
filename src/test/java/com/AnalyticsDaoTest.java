package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsDaoTest {

    private final AnalyticsDao analyticsDao = new AnalyticsDao();
    private final PlayerDao playerDao = new PlayerDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final ScoreDao scoreDao = new ScoreDao();

    // Holds every ID created by a test so finally can clean up
    private static class TestData {

        final String suffix = UUID.randomUUID().toString();

        final String aliceName = "Alice_" + suffix;
        final String bobName = "Bob_" + suffix;
        final String carolName = "Carol_" + suffix;
        final String categoryAName = "AnalyticsA_" + suffix;
        final String categoryBName = "AnalyticsB_" + suffix;

        int aliceId;
        int bobId;
        int carolId;       // never receives any scores
        int categoryAId;
        int categoryBId;   // never receives any scores
        int session1Id;
        int session2Id;

        final List<Integer> questionIds = new ArrayList<>();
        final List<Integer> scoreIds = new ArrayList<>();
    }

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    // ACCURACY BY CATEGORY
    @Test
    void testAccuracyByCategory() throws SQLException {

        TestData data = new TestData();

        try {
            seedData(data);

            AnalyticsDao.CategoryAccuracy row = analyticsDao
                    .getAccuracyByCategory()
                    .stream()
                    .filter(r -> r.categoryName()
                            .equals(data.categoryAName))
                    .findFirst()
                    .orElseThrow();

            // Alice 3 of 4 correct + Bob 1 of 2 correct = 4 of 6
            assertEquals(6, row.totalAnswers());
            assertEquals(4, row.correctAnswers());
            assertEquals(66.67, row.accuracyPercent(), 0.001);

        } finally {
            cleanup(data);
        }
    }

    // EDGE CASE: category with no scores
    @Test
    void testAccuracyOmitsCategoryWithNoScores()
            throws SQLException {

        TestData data = new TestData();

        try {
            seedData(data);

            List<AnalyticsDao.CategoryAccuracy> results =
                    analyticsDao.getAccuracyByCategory();

            assertNotNull(results);

            assertFalse(
                    results.stream()
                            .anyMatch(r -> r.categoryName()
                                    .equals(data.categoryBName))
            );

        } finally {
            cleanup(data);
        }
    }

    // TOP PLAYERS
    @Test
    void testTopPlayersRankedByPoints() throws SQLException {

        TestData data = new TestData();

        try {
            seedData(data);

            // Large limit so existing database rows cannot
            // push the test players out of the result
            List<AnalyticsDao.PlayerTotal> top =
                    analyticsDao.getTopPlayers(Integer.MAX_VALUE);

            int aliceIndex = indexOfPlayer(top, data.aliceName);
            int bobIndex = indexOfPlayer(top, data.bobName);

            assertTrue(aliceIndex >= 0);
            assertTrue(bobIndex >= 0);
            assertTrue(aliceIndex < bobIndex);

            assertEquals(30L, top.get(aliceIndex).totalPoints());
            assertEquals(5L, top.get(bobIndex).totalPoints());

            // EDGE CASE: player with no scores is not ranked
            assertEquals(-1, indexOfPlayer(top, data.carolName));

        } finally {
            cleanup(data);
        }
    }

    @Test
    void testTopPlayersLimit() throws SQLException {

        TestData data = new TestData();

        try {
            seedData(data);

            assertEquals(
                    1,
                    analyticsDao.getTopPlayers(1).size()
            );

        } finally {
            cleanup(data);
        }
    }

    @Test
    void testInvalidTopPlayersLimit() {

        assertThrows(
                IllegalArgumentException.class,
                () -> analyticsDao.getTopPlayers(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> analyticsDao.getTopPlayers(-5)
        );
    }

    // AVERAGE POINTS PER PLAYER
    @Test
    void testAveragePointsPerPlayer() throws SQLException {

        TestData data = new TestData();

        try {
            seedData(data);

            List<AnalyticsDao.PlayerAverage> averages =
                    analyticsDao.getAveragePointsPerPlayer();

            AnalyticsDao.PlayerAverage alice = averages.stream()
                    .filter(a -> a.playerName()
                            .equals(data.aliceName))
                    .findFirst()
                    .orElseThrow();

            AnalyticsDao.PlayerAverage bob = averages.stream()
                    .filter(a -> a.playerName()
                            .equals(data.bobName))
                    .findFirst()
                    .orElseThrow();

            // Alice: 30 points over 4 answers
            assertEquals(7.5, alice.averagePoints(), 0.001);

            // Bob: 5 points over 2 answers
            assertEquals(2.5, bob.averagePoints(), 0.001);

            // EDGE CASE: player with no scores does not appear
            assertFalse(
                    averages.stream()
                            .anyMatch(a -> a.playerName()
                                    .equals(data.carolName))
            );

        } finally {
            cleanup(data);
        }
    }

    // POINTS PER SESSION
    @Test
    void testPointsPerSessionOrderedByDate()
            throws SQLException {

        TestData data = new TestData();

        try {
            seedData(data);

            List<AnalyticsDao.SessionPoints> sessions =
                    analyticsDao.getPointsPerSession(data.aliceId);

            assertEquals(2, sessions.size());

            // Session 2 has the earlier date, so it comes first
            // even though session 1 was created first
            assertEquals(
                    "2026-01-01 09:00:00",
                    sessions.get(0).playedAt()
            );
            assertEquals(10L, sessions.get(0).points());

            assertEquals(
                    "2026-01-02 10:00:00",
                    sessions.get(1).playedAt()
            );
            assertEquals(20L, sessions.get(1).points());

        } finally {
            cleanup(data);
        }
    }

    // EDGE CASE: player with no scores
    @Test
    void testPointsPerSessionForPlayerWithNoScores()
            throws SQLException {

        TestData data = new TestData();

        try {
            seedData(data);

            List<AnalyticsDao.SessionPoints> sessions =
                    analyticsDao.getPointsPerSession(data.carolId);

            assertNotNull(sessions);
            assertTrue(sessions.isEmpty());

            // A player ID that does not exist behaves the same way
            assertTrue(
                    analyticsDao
                            .getPointsPerSession(Integer.MAX_VALUE)
                            .isEmpty()
            );

        } finally {
            cleanup(data);
        }
    }

    @Test
    void testInvalidPlayerIdForSessionPoints() {

        assertThrows(
                IllegalArgumentException.class,
                () -> analyticsDao.getPointsPerSession(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> analyticsDao.getPointsPerSession(-1)
        );
    }

    /*
     * Test data layout:
     *
     * Alice: session 1 -> 10 (correct), 10 (correct), 0 (wrong)
     *        session 2 -> 10 (correct)
     *        total 30 points over 4 answers
     * Bob:   session 1 -> 5 (correct), 0 (wrong)
     *        total 5 points over 2 answers
     * Carol: no scores
     * Category A: all 6 answers.  Category B: no scores.
     */
    private void seedData(TestData data) throws SQLException {

        data.aliceId = playerDao.addPlayer(data.aliceName);
        data.bobId = playerDao.addPlayer(data.bobName);
        data.carolId = playerDao.addPlayer(data.carolName);

        data.categoryAId =
                categoryDao.addCategory(data.categoryAName);
        data.categoryBId =
                categoryDao.addCategory(data.categoryBName);

        for (int i = 1; i <= 4; i++) {
            data.questionIds.add(
                    questionDao.addQuestion(
                            "Analytics question " + i
                                    + " " + data.suffix,
                            "Yes",
                            "[\"No\"]",
                            "easy",
                            data.categoryAId
                    )
            );
        }

        data.session1Id = scoreDao.createGameSession();
        data.session2Id = scoreDao.createGameSession();

        // Session 1 is created first but dated later
        setPlayedAt(data.session1Id, "2026-01-02 10:00:00");
        setPlayedAt(data.session2Id, "2026-01-01 09:00:00");

        addScore(data, data.session1Id, data.aliceId, 0, true, 10);
        addScore(data, data.session1Id, data.aliceId, 1, true, 10);
        addScore(data, data.session1Id, data.aliceId, 2, false, 0);
        addScore(data, data.session2Id, data.aliceId, 3, true, 10);

        addScore(data, data.session1Id, data.bobId, 0, true, 5);
        addScore(data, data.session1Id, data.bobId, 1, false, 0);
    }

    private void addScore(
            TestData data,
            int sessionId,
            int playerId,
            int questionIndex,
            boolean correct,
            int points
    ) throws SQLException {

        data.scoreIds.add(
                scoreDao.addScore(
                        sessionId,
                        playerId,
                        data.categoryAId,
                        data.questionIds.get(questionIndex),
                        correct,
                        points
                )
        );
    }

    // Clean up test records in dependency order
    private void cleanup(TestData data) throws SQLException {

        for (int scoreId : data.scoreIds) {
            scoreDao.deleteScore(scoreId);
        }

        for (int questionId : data.questionIds) {
            questionDao.deleteQuestion(questionId);
        }

        if (data.aliceId > 0) {
            playerDao.deletePlayer(data.aliceId);
        }
        if (data.bobId > 0) {
            playerDao.deletePlayer(data.bobId);
        }
        if (data.carolId > 0) {
            playerDao.deletePlayer(data.carolId);
        }

        if (data.categoryAId > 0) {
            categoryDao.deleteCategory(data.categoryAId);
        }
        if (data.categoryBId > 0) {
            categoryDao.deleteCategory(data.categoryBId);
        }

        if (data.session1Id > 0) {
            scoreDao.deleteGameSession(data.session1Id);
        }
        if (data.session2Id > 0) {
            scoreDao.deleteGameSession(data.session2Id);
        }
    }

    // Test-only: overwrite the timestamp so ordering is predictable
    private void setPlayedAt(int sessionId, String playedAt)
            throws SQLException {

        String sql = """
            UPDATE game_sessions
            SET played_at = ?
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, playedAt);
            statement.setInt(2, sessionId);
            statement.executeUpdate();
        }
    }

    private int indexOfPlayer(
            List<AnalyticsDao.PlayerTotal> list,
            String name
    ) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).playerName().equals(name)) {
                return i;
            }
        }

        return -1;
    }
}
