package com.triviagame.db;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the two "insight" queries: best category per player and
 * average points per game. Each test makes its own uniquely named
 * players and categories, finds them again by name, and deletes them
 * afterwards, so other data in the database does not matter.
 */
public class AnalyticsInsightsTest {

    private final AnalyticsDao analyticsDao = new AnalyticsDao();
    private final PlayerDao playerDao = new PlayerDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final ScoreDao scoreDao = new ScoreDao();

    private final String suffix = UUID.randomUUID().toString();

    private final List<Integer> scoreIds = new ArrayList<>();
    private final List<Integer> questionIds = new ArrayList<>();
    private final List<Integer> sessionIds = new ArrayList<>();
    private final List<Integer> playerIds = new ArrayList<>();
    private final List<Integer> categoryIds = new ArrayList<>();

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    @BeforeEach
    void clearLists() {
        scoreIds.clear();
        questionIds.clear();
        sessionIds.clear();
        playerIds.clear();
        categoryIds.clear();
    }

    @AfterEach
    void cleanUp() throws SQLException {

        for (int id : scoreIds) {
            scoreDao.deleteScore(id);
        }
        for (int id : questionIds) {
            questionDao.deleteQuestion(id);
        }
        for (int id : sessionIds) {
            scoreDao.deleteGameSession(id);
        }
        for (int id : playerIds) {
            playerDao.deletePlayer(id);
        }
        for (int id : categoryIds) {
            categoryDao.deleteCategory(id);
        }
    }

    // ---------- helpers ----------

    private int newPlayer(String name) throws SQLException {
        int id = playerDao.addPlayer(name + "_" + suffix);
        playerIds.add(id);
        return id;
    }

    private String playerName(String name) {
        return name + "_" + suffix;
    }

    private int newCategory(String name) throws SQLException {
        int id = categoryDao.addCategory(name + "_" + suffix);
        categoryIds.add(id);
        return id;
    }

    private int newQuestion(int categoryId) throws SQLException {
        int id = questionDao.addQuestion(
                "Insight question " + UUID.randomUUID(),
                "Yes", "[\"No\"]", "easy", categoryId
        );
        questionIds.add(id);
        return id;
    }

    private int newSession() throws SQLException {
        int id = scoreDao.createGameSession();
        sessionIds.add(id);
        return id;
    }

    // Saves `correct` right answers (10 points) and `wrong` wrong
    // answers (0 points) for one player in one category and session.
    private void answers(
            int session, int player, int category,
            int correct, int wrong
    ) throws SQLException {

        for (int i = 0; i < correct + wrong; i++) {
            boolean right = i < correct;

            scoreIds.add(scoreDao.addScore(
                    session, player, category, newQuestion(category),
                    right, right ? 10 : 0
            ));
        }
    }

    private AnalyticsDao.BestCategory bestOf(String name)
            throws SQLException {

        return analyticsDao.getBestCategoryPerPlayer().stream()
                .filter(b -> b.playerName().equals(playerName(name)))
                .findFirst()
                .orElse(null);
    }

    // ---------- best category per player ----------

    @Test
    void testBestCategoryIsTheMostAccurateOne() throws SQLException {

        int ana = newPlayer("Ana");
        int science = newCategory("Science");
        int history = newCategory("History");
        int session = newSession();

        answers(session, ana, science, 1, 2);   // 33.33 %
        answers(session, ana, history, 2, 1);   // 66.67 %

        AnalyticsDao.BestCategory best = bestOf("Ana");

        assertNotNull(best);
        assertEquals("History_" + suffix, best.categoryName());
        assertEquals(3, best.totalAnswers());
        assertEquals(2, best.correctAnswers());
        assertEquals(66.67, best.accuracyPercent(), 0.001);
    }

    @Test
    void testEachPlayerGetsTheirOwnBestCategory() throws SQLException {

        int ana = newPlayer("Ana");
        int ben = newPlayer("Ben");
        int science = newCategory("Science");
        int history = newCategory("History");
        int session = newSession();

        answers(session, ana, science, 3, 0);
        answers(session, ana, history, 0, 3);

        answers(session, ben, science, 0, 3);
        answers(session, ben, history, 3, 0);

        assertEquals("Science_" + suffix,
                bestOf("Ana").categoryName());
        assertEquals("History_" + suffix,
                bestOf("Ben").categoryName());
    }

    // EDGE CASE: equal accuracy, so the category with more answers wins
    @Test
    void testTieGoesToTheCategoryWithMoreAnswers()
            throws SQLException {

        int ana = newPlayer("Ana");
        int small = newCategory("AAA_Small");
        int large = newCategory("ZZZ_Large");
        int session = newSession();

        answers(session, ana, small, 1, 1);   // 50 %, 2 answers
        answers(session, ana, large, 2, 2);   // 50 %, 4 answers

        assertEquals("ZZZ_Large_" + suffix,
                bestOf("Ana").categoryName());
    }

    // EDGE CASE: equal accuracy and equal answers, so the name decides
    @Test
    void testFullTieGoesToTheFirstName() throws SQLException {

        int ana = newPlayer("Ana");
        int zebra = newCategory("Zebra");
        int apple = newCategory("Apple");
        int session = newSession();

        answers(session, ana, zebra, 1, 1);
        answers(session, ana, apple, 1, 1);

        assertEquals("Apple_" + suffix, bestOf("Ana").categoryName());
    }

    // EDGE CASE: a player who got everything wrong still has a best
    @Test
    void testPlayerWithOnlyWrongAnswers() throws SQLException {

        int ana = newPlayer("Ana");
        int science = newCategory("Science");
        int session = newSession();

        answers(session, ana, science, 0, 2);

        AnalyticsDao.BestCategory best = bestOf("Ana");

        assertEquals(0, best.correctAnswers());
        assertEquals(0.0, best.accuracyPercent(), 0.001);
    }

    // EDGE CASE: a player with no scores is left out
    @Test
    void testPlayerWithoutScoresIsOmitted() throws SQLException {

        newPlayer("Silent");

        assertNull(bestOf("Silent"));
    }

    // ---------- average points per game ----------

    @Test
    void testAverageAndBestGameChangeByExactlyTheNewData()
            throws SQLException {

        AnalyticsDao.GameAverage before =
                analyticsDao.getAveragePointsPerGame();

        int ana = newPlayer("Ana");
        int ben = newPlayer("Ben");
        int science = newCategory("Science");

        int session1 = newSession();
        int session2 = newSession();

        answers(session1, ana, science, 3, 0);   // game: 30 points
        answers(session1, ben, science, 1, 1);   // game: 10 points
        answers(session2, ana, science, 0, 2);   // game: 0 points

        AnalyticsDao.GameAverage after =
                analyticsDao.getAveragePointsPerGame();

        // Three new player-games worth 30 + 10 + 0 points
        assertEquals(before.games() + 3, after.games());

        double expected =
                (before.averagePoints() * before.games() + 40)
                        / (before.games() + 3);

        assertEquals(expected, after.averagePoints(), 0.0001);
        assertTrue(after.bestGamePoints() >= 30);
        assertTrue(after.bestGamePoints() >= before.bestGamePoints());
    }

    // EDGE CASE: answers saved in the same session are one game
    @Test
    void testSameSessionAnswersCountAsOneGame() throws SQLException {

        AnalyticsDao.GameAverage before =
                analyticsDao.getAveragePointsPerGame();

        int ana = newPlayer("Ana");
        int science = newCategory("Science");
        int session = newSession();

        // Two batches in the same session belong to the same game
        answers(session, ana, science, 1, 0);
        answers(session, ana, science, 1, 0);

        AnalyticsDao.GameAverage after =
                analyticsDao.getAveragePointsPerGame();

        assertEquals(before.games() + 1, after.games());
    }

    // EDGE CASE: players and sessions without answers add no games
    @Test
    void testPlayersWithoutAnswersAddNoGames() throws SQLException {

        AnalyticsDao.GameAverage before =
                analyticsDao.getAveragePointsPerGame();

        newPlayer("Silent");
        newSession();

        AnalyticsDao.GameAverage after =
                analyticsDao.getAveragePointsPerGame();

        assertEquals(before.games(), after.games());
        assertEquals(before.averagePoints(),
                after.averagePoints(), 0.0001);
    }
}
