package com.triviagame.db;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
/**
 * SQLite connection and additive schema migrations.
 * Existing scores, game sessions, players and questions are preserved.
 */
public final class Database {
 private Database() {
 }
 public static Connection getConnection() throws SQLException {
 Path databasePath = Path.of(System.getProperty("trivia.database.path", "data/trivia.db"));
 try {
 Files.createDirectories(databasePath.toAbsolutePath().getParent());
 } catch (IOException exception) {
 throw new SQLException("Cannot create database directory", exception);
 }
 Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
 try (Statement statement = connection.createStatement()) {
 statement.execute("PRAGMA foreign_keys = ON");
 statement.execute("PRAGMA busy_timeout = 5000");
 } catch (SQLException exception) {
 connection.close();
 throw exception;
 }
 return connection;
 }
 public static void initializeSchema() throws SQLException {
 try (Connection connection = getConnection()) {
 initializeSchema(connection);
 }
 }
 // Also allows migration checks against isolated databases.
 static void initializeSchema(Connection connection) throws SQLException {
 try (Statement statement = connection.createStatement()) {
 statement.execute("BEGIN IMMEDIATE");
 try {
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS categories (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL,
 parent_id INTEGER,
 FOREIGN KEY (parent_id) REFERENCES categories(id)
 )
 """);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS players (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL
 )
 """);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS game_sessions (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 played_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
 )
 """);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS questions (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 text TEXT NOT NULL,
 correct_answer TEXT NOT NULL,
 incorrect_answers TEXT NOT NULL,
 difficulty TEXT NOT NULL,
 question_type TEXT NOT NULL DEFAULT 'MULTIPLE_CHOICE',
 created_at TEXT DEFAULT CURRENT_TIMESTAMP,
 external_id TEXT,
 category_id INTEGER NOT NULL,
 FOREIGN KEY (category_id) REFERENCES categories(id)
 )
 """);
 if (!hasColumn(connection, "questions", "question_type")) {
 statement.executeUpdate("""
 ALTER TABLE questions
 ADD COLUMN question_type TEXT NOT NULL
 DEFAULT 'MULTIPLE_CHOICE'
 """);
 }
 migrateQuestions(connection);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS scores (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 session_id INTEGER NOT NULL,
 player_id INTEGER NOT NULL,
 category_id INTEGER NOT NULL,
 question_id INTEGER NOT NULL,
 is_correct INTEGER NOT NULL CHECK (is_correct IN (0, 1)),
 points INTEGER NOT NULL,
 FOREIGN KEY (session_id) REFERENCES game_sessions(id),
 FOREIGN KEY (player_id) REFERENCES players(id),
 FOREIGN KEY (category_id) REFERENCES categories(id),
 FOREIGN KEY (question_id) REFERENCES questions(id)
 )
 """);
 // Stable IDs are generated once and retained through re-export.
 // These columns are optional for old code, which keeps working.
 if (!hasColumn(connection, "game_sessions", "session_sync_id")) {
 statement.executeUpdate(
 "ALTER TABLE game_sessions ADD COLUMN session_sync_id TEXT");
 }
 if (!hasColumn(connection, "scores", "score_sync_id")) {
 statement.executeUpdate(
 "ALTER TABLE scores ADD COLUMN score_sync_id TEXT");
 }
 // Backfill any old records. Also repairs rows inserted by old
 // code before a previous version of this migration ran.
 statement.executeUpdate("""
 UPDATE game_sessions
 SET session_sync_id = lower(hex(randomblob(16)))
 WHERE session_sync_id IS NULL OR session_sync_id = ''
 """);
 statement.executeUpdate("""
 UPDATE scores
 SET score_sync_id = lower(hex(randomblob(16)))
 WHERE score_sync_id IS NULL OR score_sync_id = ''
 """);
 statement.executeUpdate("""
 CREATE UNIQUE INDEX IF NOT EXISTS
 ux_game_sessions_session_sync_id
 ON game_sessions(session_sync_id)
 """);
 statement.executeUpdate("""
 CREATE UNIQUE INDEX IF NOT EXISTS
 ux_scores_score_sync_id
 ON scores(score_sync_id)
 """);
 // New gameplay inserts through the unchanged ScoreDao still
 // receive permanent IDs automatically.
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_session_sync_id
 AFTER INSERT ON game_sessions
 WHEN NEW.session_sync_id IS NULL OR NEW.session_sync_id = ''
 BEGIN
 UPDATE game_sessions
 SET session_sync_id = lower(hex(randomblob(16)))
 WHERE id = NEW.id;
 END
 """);
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_score_sync_id
 AFTER INSERT ON scores
 WHEN NEW.score_sync_id IS NULL OR NEW.score_sync_id = ''
 BEGIN
 UPDATE scores
 SET score_sync_id = lower(hex(randomblob(16)))
 WHERE id = NEW.id;
 END
 """);
 statement.execute("COMMIT");
 } catch (SQLException | RuntimeException exception) {
 try {
 statement.execute("ROLLBACK");
 } catch (SQLException rollbackFailure) {
 exception.addSuppressed(rollbackFailure);
 }
 throw exception;
 }
 }
 }
 private static void migrateQuestions(Connection connection) throws SQLException {
 try (Statement statement = connection.createStatement()) {
 // SQLite cannot ADD COLUMN with a CURRENT_TIMESTAMP default on populated tables.
 if (!hasColumn(connection, "questions", "created_at")) {
 statement.executeUpdate("ALTER TABLE questions ADD COLUMN created_at TEXT");
 }
 if (!hasColumn(connection, "questions", "external_id")) {
 statement.executeUpdate("ALTER TABLE questions ADD COLUMN external_id TEXT");
 }
 statement.executeUpdate("""
 UPDATE questions SET created_at = CURRENT_TIMESTAMP
 WHERE created_at IS NULL OR trim(created_at) = ''
 """);
 // Preserve every legacy row and relationship. Only the oldest row retains a
 // conflicting import ID; other copies remain available with an unknown ID.
 statement.executeUpdate("""
 UPDATE questions SET external_id = NULL
 WHERE external_id = '' OR id NOT IN (
 SELECT MIN(id) FROM questions WHERE external_id IS NOT NULL GROUP BY external_id
 ) AND external_id IS NOT NULL
 """);
 statement.executeUpdate("""
 CREATE UNIQUE INDEX IF NOT EXISTS ux_questions_external_id ON questions(external_id)
 """);
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_question_created_at
 AFTER INSERT ON questions
 WHEN NEW.created_at IS NULL OR trim(NEW.created_at) = ''
 BEGIN
 UPDATE questions SET created_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
 END
 """);
 }
 }
 private static boolean hasColumn(
 Connection connection, String table, String column)
 throws SQLException {
 // Both names are constant literals controlled by this class.
 try (Statement statement = connection.createStatement();
 ResultSet result = statement.executeQuery(
 "PRAGMA table_info(" + table + ")")) {
 while (result.next()) {
 if (column.equalsIgnoreCase(result.getString("name"))) {
 return true;
 }
 }
 }
 return false;
 }
}
