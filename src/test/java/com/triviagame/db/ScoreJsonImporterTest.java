package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ScoreJsonImporterTest {

    @TempDir
    Path tempDir;

    private final PlayerDao playerDao = new PlayerDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final ScoreDao scoreDao = new ScoreDao();

    // Names and IDs for one test. Every name contains the same
    // random suffix so cleanup can find what the import created.
    private static class TestData {

        final String suffix = UUID.randomUUID().toString();

        final String categoryName = "ImportCat_" + suffix;
        final String playerOneName = "ImportPlayerOne_" + suffix;
        final String playerTwoName = "ImportPlayerTwo_" + suffix;
        final String ghostName = "ImportGhost_" + suffix;
        final String questionOne = "Import question one " + suffix;
        final String questionTwo = "Import question two " + suffix;

        int categoryId;
        int questionOneId;
        int questionTwoId;
    }

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    // VALID FILE
    @Test
    void testImportValidFile() throws Exception {

        TestData data = new TestData();
        Path file = tempDir.resolve("scores.json");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            seedData(data);

            Files.writeString(file, array(
                    entry(data.playerOneName, data.categoryName,
                            data.questionOne, "true", "10"),
                    entry(data.playerOneName, data.categoryName,
                            data.questionTwo, "false", "0"),
                    // Category lookup ignores letter case
                    entry(data.playerTwoName,
                            data.categoryName.toUpperCase(),
                            data.questionOne, "true", "10")
            ));

            ScoreJsonImporter.ImportResult result =
                    importer.importFile(file);

            assertEquals(3, result.imported());
            assertEquals(0, result.skipped());
            assertTrue(result.errors().isEmpty());

            int playerOneId =
                    findPlayerId(data.playerOneName).orElseThrow();
            int playerTwoId =
                    findPlayerId(data.playerTwoName).orElseThrow();

            List<ScoreDao.ScoreRecord> playerOneScores =
                    scoreDao.getScoresByPlayer(playerOneId);

            List<ScoreDao.ScoreRecord> playerTwoScores =
                    scoreDao.getScoresByPlayer(playerTwoId);

            assertEquals(2, playerOneScores.size());
            assertEquals(1, playerTwoScores.size());
            assertEquals(
                    10L,
                    scoreDao.getTotalPointsByPlayer(playerOneId)
            );

            ScoreDao.ScoreRecord first = playerOneScores.get(0);

            assertTrue(first.isCorrect());
            assertEquals(10, first.points());
            assertEquals(data.questionOneId, first.questionId());
            assertEquals(data.categoryId, first.categoryId());

            // One import creates exactly one session
            Set<Integer> sessionIds = new HashSet<>();

            playerOneScores.forEach(s -> sessionIds.add(s.sessionId()));
            playerTwoScores.forEach(s -> sessionIds.add(s.sessionId()));

            assertEquals(1, sessionIds.size());

        } finally {
            cleanup(data);
        }
    }

    // EXISTING PLAYER + ONE SESSION PER IMPORT
    @Test
    void testImportReusesPlayerAndCreatesSessionPerImport()
            throws Exception {

        TestData data = new TestData();
        Path file = tempDir.resolve("scores.json");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            seedData(data);

            Files.writeString(file, array(
                    entry(data.playerOneName, data.categoryName,
                            data.questionOne, "true", "10")
            ));

            assertEquals(1, importer.importFile(file).imported());
            assertEquals(1, importer.importFile(file).imported());

            // Same player found the second time, not created again
            assertEquals(1, countPlayers(data.playerOneName));

            int playerId =
                    findPlayerId(data.playerOneName).orElseThrow();

            List<ScoreDao.ScoreRecord> scores =
                    scoreDao.getScoresByPlayer(playerId);

            assertEquals(2, scores.size());

            // Two imports means two different sessions
            assertNotEquals(
                    scores.get(0).sessionId(),
                    scores.get(1).sessionId()
            );

        } finally {
            cleanup(data);
        }
    }

    // INVALID ENTRIES ARE SKIPPED AND COUNTED
    @Test
    void testImportSkipsInvalidEntries() throws Exception {

        TestData data = new TestData();
        Path file = tempDir.resolve("mixed.json");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            seedData(data);

            String missingPoints = String.format(
                    "{\"player\":\"%s\",\"category\":\"%s\","
                            + "\"questionText\":\"%s\","
                            + "\"isCorrect\":true}",
                    data.ghostName, data.categoryName,
                    data.questionOne
            );

            Files.writeString(file, array(
                    // 1: valid
                    entry(data.playerOneName, data.categoryName,
                            data.questionOne, "true", "10"),
                    // 2: missing field
                    missingPoints,
                    // 3: unknown category
                    entry(data.ghostName,
                            "NoSuchCategory_" + data.suffix,
                            data.questionOne, "true", "10"),
                    // 4: unknown question
                    entry(data.ghostName, data.categoryName,
                            "No such question " + data.suffix,
                            "true", "10"),
                    // 5: negative points
                    entry(data.ghostName, data.categoryName,
                            data.questionOne, "true", "-5"),
                    // 6: not an object
                    "42"
            ));

            ScoreJsonImporter.ImportResult result =
                    importer.importFile(file);

            assertEquals(1, result.imported());
            assertEquals(5, result.skipped());
            assertEquals(5, result.errors().size());

            assertTrue(result.errors().get(0).startsWith("Entry 2"));
            assertTrue(result.errors().get(0).contains("points"));
            assertTrue(result.errors().get(1)
                    .contains("unknown category"));
            assertTrue(result.errors().get(2)
                    .contains("unknown question"));
            assertTrue(result.errors().get(3).contains("negative"));
            assertTrue(result.errors().get(4).startsWith("Entry 6"));

            // Skipped entries must not create players
            assertTrue(findPlayerId(data.ghostName).isEmpty());

        } finally {
            cleanup(data);
        }
    }

    // EDGE CASE: empty array
    @Test
    void testImportEmptyArray() throws Exception {

        Path file = tempDir.resolve("empty.json");
        Files.writeString(file, "[]");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            ScoreJsonImporter.ImportResult result =
                    importer.importFile(file);

            assertEquals(
                    new ScoreJsonImporter.ImportResult(
                            0, 0, List.of()
                    ),
                    result
            );
        }
    }

    // EDGE CASE: malformed JSON returns an error, does not throw
    @Test
    void testImportMalformedJson() throws Exception {

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            // Truncated file
            Path truncated = tempDir.resolve("truncated.json");
            Files.writeString(truncated, "[{\"player\": \"x\", ");

            ScoreJsonImporter.ImportResult result =
                    importer.importFile(truncated);

            assertEquals(0, result.imported());
            assertEquals(0, result.skipped());
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0)
                    .startsWith("Malformed JSON"));

            // Not JSON at all
            Path garbage = tempDir.resolve("garbage.json");
            Files.writeString(garbage, "this is not json");

            result = importer.importFile(garbage);

            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0)
                    .startsWith("Malformed JSON"));
        }
    }

    // EDGE CASE: valid JSON, but not an array
    @Test
    void testImportTopLevelNotArray() throws Exception {

        Path file = tempDir.resolve("object.json");
        Files.writeString(file, "{\"player\": \"x\"}");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            ScoreJsonImporter.ImportResult result =
                    importer.importFile(file);

            assertEquals(0, result.imported());
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).contains("array"));
        }
    }

    // EDGE CASE: wrong field types
    @Test
    void testImportWrongFieldType() throws Exception {

        TestData data = new TestData();
        Path file = tempDir.resolve("types.json");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            seedData(data);

            String numericPlayer = String.format(
                    "{\"player\":123,\"category\":\"%s\","
                            + "\"questionText\":\"%s\","
                            + "\"isCorrect\":true,\"points\":10}",
                    data.categoryName, data.questionOne
            );

            Files.writeString(file, array(
                    // isCorrect as a string
                    entry(data.playerOneName, data.categoryName,
                            data.questionOne, "\"yes\"", "10"),
                    // points as a string
                    entry(data.playerOneName, data.categoryName,
                            data.questionOne, "true", "\"ten\""),
                    // points as a decimal
                    entry(data.playerOneName, data.categoryName,
                            data.questionOne, "true", "10.5"),
                    // player as a number
                    numericPlayer
            ));

            ScoreJsonImporter.ImportResult result =
                    importer.importFile(file);

            assertEquals(0, result.imported());
            assertEquals(4, result.skipped());
            assertEquals(4, result.errors().size());

            assertTrue(result.errors().get(0).contains("isCorrect"));
            assertTrue(result.errors().get(1).contains("points"));
            assertTrue(result.errors().get(2).contains("points"));
            assertTrue(result.errors().get(3).contains("player"));

            // Nothing valid, so no player was created
            assertTrue(findPlayerId(data.playerOneName).isEmpty());

        } finally {
            cleanup(data);
        }
    }

    // EDGE CASE: missing file
    @Test
    void testImportMissingFile() throws Exception {

        Path missing = tempDir.resolve("does-not-exist.json");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            ScoreJsonImporter.ImportResult result =
                    importer.importFile(missing);

            assertEquals(0, result.imported());
            assertEquals(0, result.skipped());
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().get(0).contains("not found"));

            // The async version completes normally with the same
            // result instead of failing the future
            ScoreJsonImporter.ImportResult asyncResult =
                    importer.importAsync(missing)
                            .get(10, TimeUnit.SECONDS);

            assertEquals(result, asyncResult);
        }
    }

    // ASYNC IMPORT
    @Test
    void testAsyncImport() throws Exception {

        TestData data = new TestData();
        Path file = tempDir.resolve("async.json");

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            seedData(data);

            Files.writeString(file, array(
                    entry(data.playerOneName, data.categoryName,
                            data.questionOne, "true", "10")
            ));

            CompletableFuture<ScoreJsonImporter.ImportResult> future =
                    importer.importAsync(file);

            ScoreJsonImporter.ImportResult result =
                    future.get(10, TimeUnit.SECONDS);

            assertTrue(future.isDone());
            assertEquals(1, result.imported());
            assertEquals(0, result.skipped());

        } finally {
            cleanup(data);
        }
    }

    @Test
    void testNullFile() {

        try (ScoreJsonImporter importer = new ScoreJsonImporter()) {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> importer.importFile(null)
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> importer.importAsync(null)
            );
        }
    }

    // Test helpers

    private static String entry(
            String player,
            String category,
            String questionText,
            String isCorrectJson,
            String pointsJson
    ) {
        return String.format(
                "{\"player\":\"%s\",\"category\":\"%s\","
                        + "\"questionText\":\"%s\","
                        + "\"isCorrect\":%s,\"points\":%s}",
                player, category, questionText,
                isCorrectJson, pointsJson
        );
    }

    private static String array(String... entries) {
        return "[" + String.join(",", entries) + "]";
    }

    private void seedData(TestData data) throws SQLException {

        data.categoryId =
                categoryDao.addCategory(data.categoryName);

        data.questionOneId = questionDao.addQuestion(
                data.questionOne,
                "Yes",
                "[\"No\"]",
                "easy",
                data.categoryId
        );

        data.questionTwoId = questionDao.addQuestion(
                data.questionTwo,
                "Yes",
                "[\"No\"]",
                "easy",
                data.categoryId
        );
    }

    private Optional<Integer> findPlayerId(String name)
            throws SQLException {

        return playerDao.getAllPlayers().stream()
                .filter(p -> p.name().equals(name))
                .map(PlayerDao.PlayerRecord::id)
                .findFirst();
    }

    private long countPlayers(String name) throws SQLException {

        return playerDao.getAllPlayers().stream()
                .filter(p -> p.name().equals(name))
                .count();
    }

    // Clean up test records in dependency order
    private void cleanup(TestData data) throws SQLException {

        Set<Integer> sessionIds = new HashSet<>();

        // Players (and their scores) created by the import
        for (PlayerDao.PlayerRecord player
                : playerDao.getAllPlayers()) {

            if (!player.name().endsWith(data.suffix)) {
                continue;
            }

            for (ScoreDao.ScoreRecord score
                    : scoreDao.getScoresByPlayer(player.id())) {

                scoreDao.deleteScore(score.id());
                sessionIds.add(score.sessionId());
            }

            playerDao.deletePlayer(player.id());
        }

        // Sessions created by the import
        for (int sessionId : sessionIds) {
            scoreDao.deleteGameSession(sessionId);
        }

        if (data.questionOneId > 0) {
            questionDao.deleteQuestion(data.questionOneId);
        }
        if (data.questionTwoId > 0) {
            questionDao.deleteQuestion(data.questionTwoId);
        }

        if (data.categoryId > 0) {
            categoryDao.deleteCategory(data.categoryId);
        }
    }
}
