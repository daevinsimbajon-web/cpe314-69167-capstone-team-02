package com.triviagame.db;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
/**
 * Exports every saved score, including stable score and session IDs.
 * Old import-compatible fields are kept unchanged.
 */
public final class ScoreJsonExporter {
 private static final ObjectMapper JSON = new ObjectMapper();
 public int exportFile(Path destination)
 throws IOException, SQLException {
 if (destination == null) {
 throw new IllegalArgumentException("Destination cannot be null");
 }
 // Migrates pre-existing database records without erasing them.
 Database.initializeSchema();
 String query = """
 SELECT s.score_sync_id AS score_sync_id,
 gs.session_sync_id AS session_sync_id,
 gs.played_at AS played_at,
 p.name AS player,
 c.name AS category,
 c.id AS category_id,
 q.text AS question_text,
 q.correct_answer AS correct_answer,
 q.incorrect_answers AS incorrect_answers,
 q.difficulty AS difficulty,
 q.question_type AS question_type,
 s.is_correct AS is_correct,
 s.points AS points
 FROM scores s
 JOIN game_sessions gs ON gs.id = s.session_id
 JOIN players p ON p.id = s.player_id
 JOIN categories c ON c.id = s.category_id
 JOIN questions q ON q.id = s.question_id
 ORDER BY gs.id ASC, s.id ASC
 """;
 ArrayNode entries = JSON.createArrayNode();
 try (Connection connection = Database.getConnection();
 PreparedStatement statement = connection.prepareStatement(query);
 ResultSet result = statement.executeQuery()) {
 Map<Integer, CategoryInfo> categories = loadCategories(connection);
 while (result.next()) {
 String scoreId = result.getString("score_sync_id");
 String sessionId = result.getString("session_sync_id");
 if (scoreId == null || scoreId.isBlank()
 || sessionId == null || sessionId.isBlank()) {
 throw new SQLException(
 "A score or session has no synchronization ID");
 }
 ObjectNode item = JSON.createObjectNode();
 item.put("scoreSyncId", scoreId);
 item.put("sessionSyncId", sessionId);
 item.put("playedAt", result.getString("played_at"));
 item.put("player", result.getString("player"));
 item.put("category", result.getString("category"));
 item.put("questionText", result.getString("question_text"));
 ArrayNode path = item.putArray("categoryPath");
 for (String name : categoryPath(
 result.getInt("category_id"), categories)) {
 path.add(name);
 }
 item.put("correctAnswer", result.getString("correct_answer"));
 JsonNode wrong = JSON.readTree(result.getString("incorrect_answers"));
 if (wrong == null || !wrong.isArray()) {
 throw new SQLException("Invalid incorrect_answers JSON in question bank");
 }
 item.set("incorrectAnswers", wrong);
 item.put("difficulty", result.getString("difficulty"));
 item.put("questionType", result.getString("question_type"));
 item.put("isCorrect", result.getInt("is_correct") == 1);
 item.put("points", result.getInt("points"));
 entries.add(item);
 }
 }
 Path target = destination.toAbsolutePath().normalize();
 Path folder = target.getParent();
 if (folder == null) {
 throw new IOException("Invalid export destination");
 }
 Files.createDirectories(folder);
 Path temp = Files.createTempFile(folder, "trivia-scores-", ".tmp");
 try {
 JSON.writerWithDefaultPrettyPrinter()
 .writeValue(temp.toFile(), entries);
 try {
 Files.move(temp, target,
 StandardCopyOption.REPLACE_EXISTING,
 StandardCopyOption.ATOMIC_MOVE);
 } catch (AtomicMoveNotSupportedException exception) {
 Files.move(temp, target,
 StandardCopyOption.REPLACE_EXISTING);
 }
 } finally {
 Files.deleteIfExists(temp);
 }
 return entries.size();
 }
 private record CategoryInfo(String name, Integer parentId) { }
 private static Map<Integer, CategoryInfo> loadCategories(
 Connection connection) throws SQLException {
 Map<Integer, CategoryInfo> categories = new HashMap<>();
 try (PreparedStatement statement = connection.prepareStatement(
 "SELECT id, name, parent_id FROM categories");
 ResultSet rows = statement.executeQuery()) {
 while (rows.next()) {
 int parent = rows.getInt("parent_id");
 boolean noParent = rows.wasNull();
 categories.put(rows.getInt("id"), new CategoryInfo(
 rows.getString("name"), noParent ? null : parent));
 }
 }
 return categories;
 }
 private static List<String> categoryPath(
 int id, Map<Integer, CategoryInfo> categories) throws SQLException {
 List<String> path = new ArrayList<>();
 Set<Integer> visited = new HashSet<>();
 Integer current = id;
 while (current != null) {
 if (!visited.add(current) || path.size() >= 32) {
 throw new SQLException("Category hierarchy contains a cycle or is too deep");
 }
 CategoryInfo info = categories.get(current);
 if (info == null) {
 throw new SQLException("Missing category in hierarchy: " + current);
 }
 path.add(0, info.name());
 current = info.parentId();
 }
 return path;
 }
}