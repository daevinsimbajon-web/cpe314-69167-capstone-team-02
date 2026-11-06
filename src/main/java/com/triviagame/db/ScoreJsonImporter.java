package com.triviagame.db;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Imports score history from a JSON file into SQLite.
 *
 * Expected format: an array of objects such as
 * {"player": "Ana", "category": "Science",
 *  "questionText": "What is H2O?", "isCorrect": true, "points": 10}
 *
 * Every import creates one game session (only when at least one entry
 * is saved). Invalid entries are skipped and reported in the result.
 * Problems with the file itself (missing, unreadable, malformed JSON)
 * are returned as an error result, never thrown.
 */
public class ScoreJsonImporter implements AutoCloseable {

    // Result of one import run
    public record ImportResult(
            int imported,
            int skipped,
            List<String> errors
    ) {
        public ImportResult {
            errors = errors == null
                    ? List.of()
                    : List.copyOf(errors);
        }
    }

    // One validated JSON entry
    private record Entry(
            String player,
            String category,
            String questionText,
            boolean correct,
            int points
    ) {
    }

    // Thrown internally when one entry cannot be imported
    private static class InvalidEntryException extends Exception {
        InvalidEntryException(String message) {
            super(message);
        }
    }

    // Keep the error list small for very large bad files
    private static final int MAX_ERRORS = 100;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final PlayerDao playerDao = new PlayerDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final ScoreDao scoreDao = new ScoreDao();

    // One worker thread: imports run one at a time, off the caller's
    // thread. Daemon so it never keeps the application alive.
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread =
                        new Thread(runnable, "score-json-importer");
                thread.setDaemon(true);
                return thread;
            });

    // ASYNC: Run the import on the background thread
    public CompletableFuture<ImportResult> importAsync(Path file) {

        if (file == null) {
            throw new IllegalArgumentException(
                    "File cannot be null"
            );
        }

        return CompletableFuture.supplyAsync(
                () -> importFile(file),
                executor
        );
    }

    // SYNC: Run the import on the calling thread
    public ImportResult importFile(Path file) {

        if (file == null) {
            throw new IllegalArgumentException(
                    "File cannot be null"
            );
        }

        if (!Files.isRegularFile(file)) {
            return failure("File not found: " + file);
        }

        JsonNode root;

        try (InputStream input = Files.newInputStream(file)) {
            root = mapper.readTree(input);

        } catch (JsonProcessingException e) {
            return failure("Malformed JSON" + describe(e));

        } catch (IOException e) {
            return failure("Cannot read file: " + e.getMessage());
        }

        if (root == null || root.isMissingNode()) {
            return failure("File is empty");
        }

        if (!root.isArray()) {
            return failure(
                    "Top-level JSON value must be an array"
            );
        }

        if (root.isEmpty()) {
            return new ImportResult(0, 0, List.of());
        }

        try {
            return importEntries(root);

        } catch (SQLException e) {
            // Only setup queries reach here; entry-level database
            // errors are handled per entry, so nothing was saved.
            return failure("Database error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    // Import every entry, skipping the invalid ones
    private ImportResult importEntries(JsonNode entries)
            throws SQLException {

        // Load lookup data once instead of once per entry
        List<CategoryDao.CategoryRecord> categories =
                categoryDao.getAllCategories();

        Map<String, Integer> playerIds = new HashMap<>();

        for (PlayerDao.PlayerRecord player
                : playerDao.getAllPlayers()) {

            playerIds.putIfAbsent(
                    normalize(player.name()),
                    player.id()
            );
        }

        Map<Integer, List<QuestionDao.QuestionRecord>>
                questionCache = new HashMap<>();

        List<String> errors = new ArrayList<>();

        int sessionId = 0;
        int imported = 0;
        int skipped = 0;
        int number = 0;

        for (JsonNode node : entries) {

            number++;

            try {
                Entry entry = parseEntry(node);

                QuestionDao.QuestionRecord question =
                        findQuestion(
                                categories,
                                questionCache,
                                entry
                        );

                // Only create the player once the entry is
                // known to be valid
                int playerId =
                        findOrCreatePlayer(playerIds, entry.player());

                // One session per import, created on first use
                if (sessionId == 0) {
                    sessionId = scoreDao.createGameSession();
                }

                scoreDao.addScore(
                        sessionId,
                        playerId,
                        question.categoryId(),
                        question.id(),
                        entry.correct(),
                        entry.points()
                );

                imported++;

            } catch (InvalidEntryException e) {
                skipped++;
                addError(errors,
                        "Entry " + number + ": " + e.getMessage());

            } catch (SQLException e) {
                skipped++;
                addError(errors,
                        "Entry " + number
                                + ": database error: "
                                + e.getMessage());
            }
        }

        int unlisted = skipped - errors.size();

        if (unlisted > 0) {
            errors.add(
                    "... and " + unlisted
                            + " more skipped entries not listed"
            );
        }

        return new ImportResult(imported, skipped, errors);
    }

    // Validate one JSON entry (strict types, no coercion)
    private Entry parseEntry(JsonNode node)
            throws InvalidEntryException {

        if (!node.isObject()) {
            throw new InvalidEntryException(
                    "entry is not a JSON object"
            );
        }

        String player = requireText(node, "player");
        String category = requireText(node, "category");
        String questionText = requireText(node, "questionText");
        boolean correct = requireBoolean(node, "isCorrect");
        int points = requireInt(node, "points");

        if (points < 0) {
            throw new InvalidEntryException(
                    "points cannot be negative"
            );
        }

        return new Entry(
                player,
                category,
                questionText,
                correct,
                points
        );
    }

    private String requireText(JsonNode node, String field)
            throws InvalidEntryException {

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            throw new InvalidEntryException(
                    "missing field '" + field + "'"
            );
        }

        if (!value.isTextual()) {
            throw new InvalidEntryException(
                    "field '" + field + "' must be a string"
            );
        }

        String text = value.textValue().trim();

        if (text.isEmpty()) {
            throw new InvalidEntryException(
                    "field '" + field + "' cannot be blank"
            );
        }

        return text;
    }

    private boolean requireBoolean(JsonNode node, String field)
            throws InvalidEntryException {

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            throw new InvalidEntryException(
                    "missing field '" + field + "'"
            );
        }

        if (!value.isBoolean()) {
            throw new InvalidEntryException(
                    "field '" + field + "' must be a boolean"
            );
        }

        return value.booleanValue();
    }

    private int requireInt(JsonNode node, String field)
            throws InvalidEntryException {

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            throw new InvalidEntryException(
                    "missing field '" + field + "'"
            );
        }

        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new InvalidEntryException(
                    "field '" + field + "' must be an integer"
            );
        }

        return value.intValue();
    }

    // Find the question by category name and question text
    private QuestionDao.QuestionRecord findQuestion(
            List<CategoryDao.CategoryRecord> categories,
            Map<Integer, List<QuestionDao.QuestionRecord>> cache,
            Entry entry
    ) throws SQLException, InvalidEntryException {

        String categoryKey = normalize(entry.category());
        String questionKey = normalize(entry.questionText());

        boolean categoryFound = false;

        for (CategoryDao.CategoryRecord category : categories) {

            if (!normalize(category.name()).equals(categoryKey)) {
                continue;
            }

            categoryFound = true;

            List<QuestionDao.QuestionRecord> questions =
                    cache.get(category.id());

            if (questions == null) {
                questions = questionDao
                        .getQuestionsByCategory(category.id());

                cache.put(category.id(), questions);
            }

            for (QuestionDao.QuestionRecord question : questions) {
                if (normalize(question.text())
                        .equals(questionKey)) {
                    return question;
                }
            }
        }

        if (!categoryFound) {
            throw new InvalidEntryException(
                    "unknown category '" + entry.category() + "'"
            );
        }

        throw new InvalidEntryException(
                "unknown question '" + entry.questionText()
                        + "' in category '" + entry.category() + "'"
        );
    }

    // Find an existing player by name or create a new one
    private int findOrCreatePlayer(
            Map<String, Integer> playerIds,
            String name
    ) throws SQLException {

        String key = normalize(name);

        Integer existing = playerIds.get(key);

        if (existing != null) {
            return existing;
        }

        int playerId = playerDao.addPlayer(name);

        playerIds.put(key, playerId);

        return playerId;
    }

    private static void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        }
    }

    private static ImportResult failure(String message) {
        return new ImportResult(0, 0, List.of(message));
    }

    private static String describe(JsonProcessingException e) {

        JsonLocation location = e.getLocation();

        String where = "";

        if (location != null && location.getLineNr() >= 0) {
            where = " at line " + location.getLineNr()
                    + ", column " + location.getColumnNr();
        }

        return where + ": " + e.getOriginalMessage();
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
