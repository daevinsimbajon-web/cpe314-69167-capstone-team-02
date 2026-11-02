package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseTest {

    @BeforeAll
    static void setup() throws SQLException {
        Database.initializeSchema();
    }

    @Test
    void testDatabaseConnection() throws SQLException {

        try (Connection connection =
                     Database.getConnection()) {

            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void testDatabaseFileCreated() {

        assertTrue(
            Files.exists(Path.of("data", "trivia.db"))
        );
    }

    @Test
    void testDatabaseTables() throws SQLException {

        String[] tables = {
            "categories",
            "players",
            "game_sessions",
            "questions",
            "scores"
        };

        String sql = """
            SELECT name
            FROM sqlite_master
            WHERE type = 'table'
            AND name = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            for (String table : tables) {

                statement.setString(1, table);

                try (ResultSet result =
                             statement.executeQuery()) {

                    assertTrue(
                        result.next(),
                        "Missing table: " + table
                    );
                }
            }
        }
    }

    @Test
    void testInsertAndSelectPlayer()
            throws SQLException {

        String name = "Test_" + UUID.randomUUID();

        String insertSql =
            "INSERT INTO players (name) VALUES (?)";

        String selectSql =
            "SELECT name FROM players WHERE name = ?";

        try (Connection connection =
                     Database.getConnection()) {

            connection.setAutoCommit(false);

            try {
                // INSERT
                try (PreparedStatement insert =
                         connection.prepareStatement(insertSql)) {

                    insert.setString(1, name);

                    assertEquals(
                        1,
                        insert.executeUpdate()
                    );
                }

                // SELECT
                try (PreparedStatement select =
                         connection.prepareStatement(selectSql)) {

                    select.setString(1, name);

                    try (ResultSet result =
                             select.executeQuery()) {

                        assertTrue(result.next());

                        assertEquals(
                            name,
                            result.getString("name")
                        );
                    }
                }
            } finally {
                // Leave no test player in the database.
                connection.rollback();
            }
        }
    }
}
