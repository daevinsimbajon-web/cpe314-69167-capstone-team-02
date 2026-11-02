package com.triviagame.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static final String DB_URL =
            "jdbc:sqlite:data/trivia.db";

    private Database() {
    }

    public static Connection getConnection()
            throws SQLException {

        try {
            Files.createDirectories(Path.of("data"));
        } catch (IOException e) {
            throw new SQLException(
                    "Cannot create database directory", e);
        }

        Connection connection =
                DriverManager.getConnection(DB_URL);

        try (Statement statement =
                     connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON");

        } catch (SQLException e) {
            connection.close();
            throw e;
        }

        return connection;
    }

    public static void initializeSchema()
            throws SQLException {

        try (Connection connection = getConnection();
             Statement statement =
                     connection.createStatement()) {

            // CATEGORY TABLE
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    parent_id INTEGER,
                    FOREIGN KEY (parent_id)
                        REFERENCES categories(id)
                )
                """);

            // PLAYER TABLE
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS players (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL
                )
                """);

            // GAME SESSION TABLE
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS game_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    played_at TEXT NOT NULL
                        DEFAULT CURRENT_TIMESTAMP
                )
                """);

            // QUESTION TABLE
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT NOT NULL,
                    correct_answer TEXT NOT NULL,
                    incorrect_answers TEXT NOT NULL,
                    difficulty TEXT NOT NULL,
                    question_type TEXT NOT NULL DEFAULT 
            		    'MULTIPLE_CHOICE',
                    category_id INTEGER NOT NULL,
                    FOREIGN KEY (category_id)
                        REFERENCES categories(id)
                )
                """);
            
        boolean hasQuestionType = false;

        try (var columns = connection.getMetaData()
                    .getColumns(null, null, "questions", "question_type")) {

        	hasQuestionType = columns.next();
        }

        if (!hasQuestionType) {
        	statement.executeUpdate("""
                    ALTER TABLE questions
                    ADD COLUMN question_type TEXT NOT NULL
                    DEFAULT 'MULTIPLE_CHOICE'
                    """);
        }

            // SCORE TABLE
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS scores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL,
                    player_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL,
                    question_id INTEGER NOT NULL,
                    is_correct INTEGER NOT NULL
                        CHECK (is_correct IN (0, 1)),
                    points INTEGER NOT NULL,
                    FOREIGN KEY (session_id)
                        REFERENCES game_sessions(id),
                    FOREIGN KEY (player_id)
                        REFERENCES players(id),
                    FOREIGN KEY (category_id)
                        REFERENCES categories(id),
                    FOREIGN KEY (question_id)
                        REFERENCES questions(id)
                )
                """);
        }
    }
}
