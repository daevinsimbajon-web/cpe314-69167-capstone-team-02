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
 private static final String DB_URL = "jdbc:sqlite:data/trivia.db";
 private Database() {
 }
 public static Connection getConnection() throws SQLException {
 try {
 Files.createDirectories(Path.of("data"));
 } catch (IOException exception) {
 throw new SQLException("Cannot create database directory", exception);
 }
 Connection connection = DriverManager.getConnection(DB_URL);
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
 try (Connection connection = getConnection();
 Statement statement = connection.createStatement()) {
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