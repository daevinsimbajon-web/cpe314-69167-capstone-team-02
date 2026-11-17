package com.triviagame.db;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * Imports score records from JSON. New exports contain scoreSyncId and
 * sessionSyncId, so repeated imports skip previously imported scores.
 * Old five-field JSON remains accepted for backward compatibility,
 * but those old files cannot be reliably deduplicated.
 */
public final class ScoreJsonImporter implements AutoCloseable {
 public record ImportResult(
 int imported,
 int skipped,
 List<String> errors,
 int duplicates) {
 public ImportResult {
 errors = errors == null ? List.of() : List.copyOf(errors);
 }
 // Keep compatibility with older tests and UI code.
 public ImportResult(int imported, int skipped, List<String> errors) {
 this(imported, skipped, errors, 0);
 }
 }
 private record Entry(
 String player,
 String category,
 String questionText,
 boolean correct,
 int points,
 String scoreSyncId,
 String sessionSyncId,
 String playedAt,
 QuestionDetails questionDetails) {
 boolean synchronizedEntry() {
 return scoreSyncId != null;
 }
 }
 private record QuestionMatch(int id, int categoryId) {
 }
 private record QuestionDetails(
 List<String> categoryPath,
 String correctAnswer,
 List<String> incorrectAnswers,
 String difficulty,
 String questionType) {
 }
 private static final class InvalidEntryException extends Exception {
 InvalidEntryException(String message) {
 super(message);
 }
 }
 private static final int MAX_ERRORS = 100;
 private static final ObjectMapper MAPPER = new ObjectMapper()
 .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 private final ExecutorService executor =
 Executors.newSingleThreadExecutor(runnable -> {
 Thread thread = new Thread(runnable, "score-json-importer");
 thread.setDaemon(true);
 return thread;
 });
 public CompletableFuture<ImportResult> importAsync(Path file) {
 if (file == null) {
 throw new IllegalArgumentException("File cannot be null");
 }
 return CompletableFuture.supplyAsync(() -> importFile(file), executor);
 }
 public ImportResult importFile(Path file) {
 if (file == null) {
 throw new IllegalArgumentException("File cannot be null");
 }
 if (!Files.isRegularFile(file)) {
 return failure("File not found: " + file);
 }
 JsonNode root;
 try (InputStream input = Files.newInputStream(file)) {
 root = MAPPER.readTree(input);
 } catch (JsonProcessingException exception) {
 return failure("Malformed JSON" + describe(exception));
 } catch (IOException exception) {
 return failure("Cannot read file: " + exception.getMessage());
 }
 if (root == null || root.isMissingNode()) {
 return failure("File is empty");
 }
 if (!root.isArray()) {
 return failure("Top-level JSON value must be an array");
 }
 if (root.isEmpty()) {
 return new ImportResult(0, 0, List.of(), 0);
 }
 try {
 Database.initializeSchema();
 return importEntries(root);
 } catch (SQLException exception) {
 return failure("Database error: " + exception.getMessage());
 }
 }
 private ImportResult importEntries(JsonNode entries)
 throws SQLException {
 int imported = 0;
 int skipped = 0;
 int duplicates = 0;
 int number = 0;
 int legacySessionId = 0;
 List<String> errors = new ArrayList<>();
 try (Connection connection = Database.getConnection()) {
 for (JsonNode node : entries) {
 number++;
 try {
 Entry entry = parseEntry(node);
 // Every score is one transaction, so invalid data
 // cannot create an orphaned player or game session.
 connection.setAutoCommit(false);
 try {
 if (entry.synchronizedEntry()
 && scoreAlreadyExists(
 connection, entry.scoreSyncId())) {
 connection.rollback();
 duplicates++;
 skipped++;
 continue;
 }
 QuestionMatch question = entry.questionDetails() == null
 ? findQuestion(connection, entry.category(),
 entry.questionText())
 : findOrCreateQuestion(connection, entry);
 int playerId = findOrCreatePlayer(
 connection, entry.player());
 int sessionId;
 if (entry.synchronizedEntry()) {
 sessionId = findOrCreateSyncedSession(
 connection,
 entry.sessionSyncId(),
 entry.playedAt());
 } else if (legacySessionId > 0) {
 sessionId = legacySessionId;
 } else {
 sessionId = createLegacySession(connection);
 }
 insertScore(connection, entry, sessionId,
 playerId, question);
 connection.commit();
 imported++;
 if (!entry.synchronizedEntry()) {
 legacySessionId = sessionId;
 }
 } catch (InvalidEntryException | SQLException exception) {
 connection.rollback();
 throw exception;
 } finally {
 connection.setAutoCommit(true);
 }
 } catch (InvalidEntryException exception) {
 skipped++;
 addError(errors, "Entry " + number + ": "
 + exception.getMessage());
 } catch (SQLException exception) {
 skipped++;
 addError(errors, "Entry " + number
 + ": database error: " + exception.getMessage());
 }
 }
 }
 int unlisted = skipped - duplicates;
 if (unlisted > MAX_ERRORS) {
 addError(errors, "Some additional invalid entries were omitted.");
 }
 return new ImportResult(imported, skipped, errors, duplicates);
 }
 private static boolean scoreAlreadyExists(
 Connection connection, String scoreId) throws SQLException {
 String sql = "SELECT 1 FROM scores WHERE score_sync_id = ? LIMIT 1";
 try (PreparedStatement statement = connection.prepareStatement(sql)) {
 statement.setString(1, scoreId);
 try (ResultSet result = statement.executeQuery()) {
 return result.next();
 }
 }
 }
 private static QuestionMatch findQuestion(
 Connection connection, String category, String text)
 throws SQLException, InvalidEntryException {
 String sql = """
 SELECT q.id, q.category_id
 FROM questions q
 JOIN categories c ON c.id = q.category_id
 WHERE trim(c.name) = ? COLLATE NOCASE
 AND trim(q.text) = ? COLLATE NOCASE
 ORDER BY q.id ASC
 LIMIT 1
 """;
 try (PreparedStatement statement = connection.prepareStatement(sql)) {
 statement.setString(1, category.trim());
 statement.setString(2, text.trim());
 try (ResultSet result = statement.executeQuery()) {
 if (result.next()) {
 return new QuestionMatch(
 result.getInt("id"),
 result.getInt("category_id"));
 }
 }
 }
 try (PreparedStatement categoryStatement =
 connection.prepareStatement(
 "SELECT 1 FROM categories "
 + "WHERE trim(name) = ? COLLATE NOCASE LIMIT 1")) {
 categoryStatement.setString(1, category.trim());
 try (ResultSet result = categoryStatement.executeQuery()) {
 if (!result.next()) {
 throw new InvalidEntryException(
 "unknown category '" + category + "'");
 }
 }
 }
 throw new InvalidEntryException(
 "unknown question '" + text + "' in category '"
 + category + "'. Import the question bank first.");
 }
 /**
 * A self-contained export includes all data required to recreate its
 * questions. Missing categories and questions are added in the same
 * transaction as the score; rollback removes any partial inserts.
 */
 private static QuestionMatch findOrCreateQuestion(
 Connection connection, Entry entry)
 throws SQLException, InvalidEntryException {
 QuestionDetails details = entry.questionDetails();
 Integer parentId = null;
 for (String name : details.categoryPath()) {
 Integer nextId = null;
 String query = parentId == null
 ? "SELECT id FROM categories WHERE parent_id IS NULL "
 + "AND trim(name) = ? COLLATE NOCASE ORDER BY id LIMIT 1"
 : "SELECT id FROM categories WHERE parent_id = ? "
 + "AND trim(name) = ? COLLATE NOCASE ORDER BY id LIMIT 1";
 try (PreparedStatement statement = connection.prepareStatement(query)) {
 if (parentId == null) {
 statement.setString(1, name);
 } else {
 statement.setInt(1, parentId);
 statement.setString(2, name);
 }
 try (ResultSet row = statement.executeQuery()) {
 if (row.next()) {
 nextId = row.getInt(1);
 }
 }
 }
 if (nextId == null) {
 try (PreparedStatement statement = connection.prepareStatement(
 "INSERT INTO categories(name, parent_id) VALUES(?, ?)")) {
 statement.setString(1, name);
 if (parentId == null) {
 statement.setNull(2, java.sql.Types.INTEGER);
 } else {
 statement.setInt(2, parentId);
 }
 statement.executeUpdate();
 }
 nextId = lastInsertedId(connection);
 }
 parentId = nextId;
 }
 int categoryId = parentId;
 boolean sameTextExists = false;
 try (PreparedStatement statement = connection.prepareStatement(
 "SELECT id, correct_answer, difficulty, question_type "
 + "FROM questions WHERE category_id = ? "
 + "AND trim(text) = ? COLLATE NOCASE ORDER BY id")) {
 statement.setInt(1, categoryId);
 statement.setString(2, entry.questionText());
 try (ResultSet row = statement.executeQuery()) {
 while (row.next()) {
 sameTextExists = true;
 if (row.getString("correct_answer").equalsIgnoreCase(
 details.correctAnswer())
 && row.getString("difficulty").equalsIgnoreCase(
 details.difficulty())
 && row.getString("question_type").equalsIgnoreCase(
 details.questionType())) {
 return new QuestionMatch(row.getInt("id"), categoryId);
 }
 }
 }
 }
 if (sameTextExists) {
 throw new InvalidEntryException(
 "Question already exists with a conflicting answer, "
 + "difficulty, or type: '" + entry.questionText() + "'");
 }
 String wrongJson;
 try {
 wrongJson = MAPPER.writeValueAsString(details.incorrectAnswers());
 } catch (JsonProcessingException exception) {
 throw new InvalidEntryException("Invalid question choices");
 }
 try (PreparedStatement statement = connection.prepareStatement(
 "INSERT INTO questions(text, correct_answer, "
 + "incorrect_answers, difficulty, category_id, question_type) "
 + "VALUES (?, ?, ?, ?, ?, ?)")) {
 statement.setString(1, entry.questionText());
 statement.setString(2, details.correctAnswer());
 statement.setString(3, wrongJson);
 statement.setString(4, details.difficulty());
 statement.setInt(5, categoryId);
 statement.setString(6, details.questionType());
 statement.executeUpdate();
 }
 return new QuestionMatch(lastInsertedId(connection), categoryId);
 }
 private static int findOrCreatePlayer(
 Connection connection, String name) throws SQLException {
 try (PreparedStatement statement = connection.prepareStatement(
 "SELECT id FROM players WHERE name = ? COLLATE NOCASE "
 + "ORDER BY id LIMIT 1")) {
 statement.setString(1, name);
 try (ResultSet result = statement.executeQuery()) {
 if (result.next()) {
 return result.getInt(1);
 }
 }
 }
 try (PreparedStatement statement = connection.prepareStatement(
 "INSERT INTO players(name) VALUES(?)",
 Statement.RETURN_GENERATED_KEYS)) {
 statement.setString(1, name);
 statement.executeUpdate();
 }
 return lastInsertedId(connection);
 }
 private static int findOrCreateSyncedSession(
 Connection connection, String syncId, String playedAt)
 throws SQLException {
 try (PreparedStatement statement = connection.prepareStatement(
 "SELECT id FROM game_sessions WHERE session_sync_id = ?")) {
 statement.setString(1, syncId);
 try (ResultSet result = statement.executeQuery()) {
 if (result.next()) {
 return result.getInt(1);
 }
 }
 }
 if (playedAt == null || playedAt.isBlank()) {
 try (PreparedStatement statement = connection.prepareStatement(
 "INSERT INTO game_sessions(session_sync_id) VALUES(?)")) {
 statement.setString(1, syncId);
 statement.executeUpdate();
 }
 } else {
 try (PreparedStatement statement = connection.prepareStatement(
 "INSERT INTO game_sessions(played_at, session_sync_id) "
 + "VALUES(?, ?)")) {
 statement.setString(1, playedAt);
 statement.setString(2, syncId);
 statement.executeUpdate();
 }
 }
 return lastInsertedId(connection);
 }
 private static int createLegacySession(Connection connection)
 throws SQLException {
 try (Statement statement = connection.createStatement()) {
 statement.executeUpdate(
 "INSERT INTO game_sessions(played_at) "
 + "VALUES(CURRENT_TIMESTAMP)");
 }
 return lastInsertedId(connection);
 }
 private static void insertScore(
 Connection connection, Entry entry, int sessionId,
 int playerId, QuestionMatch question) throws SQLException {
 String sql = """
 INSERT INTO scores(
 session_id, player_id, category_id, question_id,
 is_correct, points, score_sync_id
 ) VALUES (?, ?, ?, ?, ?, ?, ?)
 """;
 try (PreparedStatement statement = connection.prepareStatement(sql)) {
 statement.setInt(1, sessionId);
 statement.setInt(2, playerId);
 statement.setInt(3, question.categoryId());
 statement.setInt(4, question.id());
 statement.setInt(5, entry.correct() ? 1 : 0);
 statement.setInt(6, entry.points());
 statement.setString(7, entry.scoreSyncId());
 statement.executeUpdate();
 }
 }
 private static int lastInsertedId(Connection connection)
 throws SQLException {
 try (Statement statement = connection.createStatement();
 ResultSet result = statement.executeQuery(
 "SELECT last_insert_rowid()")) {
 if (result.next()) {
 return result.getInt(1);
 }
 }
 throw new SQLException("Could not retrieve inserted ID");
 }
 private static Entry parseEntry(JsonNode node)
 throws InvalidEntryException {
 if (!node.isObject()) {
 throw new InvalidEntryException("entry is not an object");
 }
 String player = requireText(node, "player");
 String category = requireText(node, "category");
 String question = requireText(node, "questionText");
 boolean correct = requireBoolean(node, "isCorrect");
 int points = requireInteger(node, "points");
 if (points < 0) {
 throw new InvalidEntryException("points cannot be negative");
 }
 boolean hasScoreId = node.hasNonNull("scoreSyncId");
 boolean hasSessionId = node.hasNonNull("sessionSyncId");
 if (hasScoreId != hasSessionId) {
 throw new InvalidEntryException(
 "scoreSyncId and sessionSyncId must both be present");
 }
 String scoreId = null;
 String sessionId = null;
 String playedAt = null;
 if (hasScoreId) {
 scoreId = requireText(node, "scoreSyncId");
 sessionId = requireText(node, "sessionSyncId");
 if (!scoreId.matches("[a-fA-F0-9]{32}")
 || !sessionId.matches("[a-fA-F0-9]{32}")) {
 throw new InvalidEntryException(
 "sync IDs must contain 32 hexadecimal characters");
 }
 scoreId = scoreId.toLowerCase(java.util.Locale.ROOT);
 sessionId = sessionId.toLowerCase(java.util.Locale.ROOT);
 if (node.hasNonNull("playedAt")) {
 playedAt = requireText(node, "playedAt");
 }
 }
 QuestionDetails details = parseQuestionDetails(node, category);
 return new Entry(player, category, question,
 correct, points, scoreId, sessionId, playedAt, details);
 }
 private static QuestionDetails parseQuestionDetails(
 JsonNode node, String category) throws InvalidEntryException {
 // Old score-only exports are supported, but cannot recreate
 // questions that do not exist in the receiving database.
 boolean any = node.has("correctAnswer")
 || node.has("incorrectAnswers")
 || node.has("difficulty")
 || node.has("questionType")
 || node.has("categoryPath");
 if (!any) {
 return null;
 }
 String correct = requireText(node, "correctAnswer");
 String difficulty = requireText(node, "difficulty")
 .toLowerCase(Locale.ROOT);
 String type = requireText(node, "questionType")
 .toUpperCase(Locale.ROOT);
 if (!List.of("easy", "medium", "hard").contains(difficulty)) {
 throw new InvalidEntryException("invalid difficulty");
 }
 if (!List.of("MULTIPLE_CHOICE", "TRUE_FALSE").contains(type)) {
 throw new InvalidEntryException("invalid question type");
 }
 JsonNode wrongArray = node.get("incorrectAnswers");
 if (wrongArray == null || !wrongArray.isArray()) {
 throw new InvalidEntryException("incorrectAnswers must be an array");
 }
 List<String> wrong = new ArrayList<>();
 for (JsonNode choice : wrongArray) {
 if (!choice.isTextual() || choice.asText().trim().isEmpty()) {
 throw new InvalidEntryException("incorrect answer must be text");
 }
 String value = choice.asText().trim();
 if (value.equalsIgnoreCase(correct)) {
 throw new InvalidEntryException(
 "correct answer cannot be an incorrect answer");
 }
 wrong.add(value);
 }
 if (type.equals("TRUE_FALSE")) {
 if (!List.of("true", "false").contains(
 correct.toLowerCase(Locale.ROOT)) || wrong.size() != 1
 || !List.of("true", "false").contains(
 wrong.get(0).toLowerCase(Locale.ROOT))) {
 throw new InvalidEntryException("invalid True/False answers");
 }
 } else if (wrong.isEmpty()) {
 throw new InvalidEntryException("multiple-choice answers missing");
 }
 List<String> path = new ArrayList<>();
 JsonNode pathNode = node.get("categoryPath");
 if (pathNode == null) {
 path.add(category);
 } else {
 if (!pathNode.isArray() || pathNode.isEmpty()
 || pathNode.size() > 32) {
 throw new InvalidEntryException("invalid categoryPath");
 }
 for (JsonNode part : pathNode) {
 if (!part.isTextual() || part.asText().trim().isEmpty()) {
 throw new InvalidEntryException("invalid categoryPath entry");
 }
 path.add(part.asText().trim());
 }
 if (!path.get(path.size() - 1).equalsIgnoreCase(category)) {
 throw new InvalidEntryException(
 "category must match final categoryPath element");
 }
 }
 return new QuestionDetails(
 List.copyOf(path), correct, List.copyOf(wrong),
 difficulty, type);
 }
 private static String requireText(JsonNode node, String name)
 throws InvalidEntryException {
 JsonNode value = node.get(name);
 if (value == null || !value.isTextual()
 || value.asText().trim().isEmpty()) {
 throw new InvalidEntryException(
 "field '" + name + "' must be non-empty text");
 }
 return value.asText().trim();
 }
 private static boolean requireBoolean(JsonNode node, String name)
 throws InvalidEntryException {
 JsonNode value = node.get(name);
 if (value == null || !value.isBoolean()) {
 throw new InvalidEntryException(
 "field '" + name + "' must be a boolean");
 }
 return value.booleanValue();
 }
 private static int requireInteger(JsonNode node, String name)
 throws InvalidEntryException {
 JsonNode value = node.get(name);
 if (value == null || !value.isIntegralNumber()
 || !value.canConvertToInt()) {
 throw new InvalidEntryException(
 "field '" + name + "' must be an integer");
 }
 return value.intValue();
 }
 private static ImportResult failure(String message) {
 return new ImportResult(0, 0, List.of(message), 0);
 }
 private static void addError(List<String> errors, String message) {
 if (errors.size() < MAX_ERRORS) {
 errors.add(message);
 }
 }
 private static String describe(JsonProcessingException exception) {
 JsonLocation location = exception.getLocation();
 String where = location == null ? ""
 : " at line " + location.getLineNr()
 + ", column " + location.getColumnNr();
 return where + ": " + exception.getOriginalMessage();
 }
 @Override
 public void close() {
 executor.shutdown();
 }
}