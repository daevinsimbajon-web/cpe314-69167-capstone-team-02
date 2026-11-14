package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseTest {

    @TempDir
    Path directory;

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
            Files.exists(Path.of(System.getProperty("trivia.database.path", "data/trivia.db")))
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

    @Test
    void corruptedDatabaseFailsWithControlledSqlException() throws Exception {
        Path file = directory.resolve("corrupted.db");
        try (Connection connection = open(file)) {
            Database.initializeSchema(connection);
        }

        // Damage only this test's SQLite header, after closing its connection.
        try (RandomAccessFile bytes = new RandomAccessFile(file.toFile(), "rw")) {
            bytes.write("Not a SQLite DB!".getBytes(StandardCharsets.US_ASCII));
        }

        SQLException failure = assertThrows(SQLException.class, () -> {
            try (Connection connection = open(file)) {
                Database.initializeSchema(connection);
            }
        });

        assertEquals(26, failure.getErrorCode(), "Expected SQLITE_NOTADB");
    }

    @Test
    void lockedDatabaseReportsBusyThenRecoversWithoutLosingData() throws Exception {
        Path file = directory.resolve("locked.db");
        try (Connection writer = open(file)) {
            Database.initializeSchema(writer);
            try (Statement statement = writer.createStatement()) {
                statement.executeUpdate("INSERT INTO players (id, name) VALUES (1, 'Saved player')");
            }

            try (Connection competitor = open(file);
                 Statement competingStatement = competitor.createStatement()) {
                competingStatement.execute("PRAGMA busy_timeout = 100");
                writer.setAutoCommit(false);
                try {
                    try (Statement statement = writer.createStatement()) {
                        statement.executeUpdate("UPDATE players SET name = 'Uncommitted' WHERE id = 1");
                    }

                    assertTimeout(Duration.ofSeconds(3), () -> {
                        SQLException failure = assertThrows(SQLException.class,
                                () -> Database.initializeSchema(competitor));
                        assertEquals(5, failure.getErrorCode(), "Expected SQLITE_BUSY");
                    });
                    assertSavedPlayer(competitor);
                } finally {
                    writer.rollback();
                }

                // Retry on the same connection: failure must not leave a transaction open.
                assertTimeout(Duration.ofSeconds(3), () -> Database.initializeSchema(competitor));
                assertSavedPlayer(competitor);
                assertEquals(1, competingStatement.executeUpdate(
                        "INSERT INTO players (id, name) VALUES (2, 'After lock release')"));
            }
        }
        try (Connection reopened = open(file)) {
            assertSavedPlayer(reopened);
            try (Statement statement = reopened.createStatement();
                 ResultSet result = statement.executeQuery("SELECT name FROM players WHERE id = 2")) {
                assertTrue(result.next());
                assertEquals("After lock release", result.getString(1));
            }
        }
    }

    @Test
    void constraintFailureCanBeRolledBackAndConnectionReused() throws Exception {
        Path file = directory.resolve("recoverable.db");
        try (Connection connection = open(file)) {
            Database.initializeSchema(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO players (id, name) VALUES (1, 'Saved player')");
                connection.setAutoCommit(false);
                try {
                    statement.executeUpdate("UPDATE players SET name = 'Pending edit' WHERE id = 1");
                    SQLException failure = assertThrows(SQLException.class, () ->
                            statement.executeUpdate("INSERT INTO players (name) VALUES (NULL)"));
                    assertEquals(19, failure.getErrorCode() & 0xff, "Expected SQLITE_CONSTRAINT");
                } finally {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
                assertSavedPlayer(connection);
                assertEquals(1, statement.executeUpdate(
                        "INSERT INTO players (id, name) VALUES (2, 'Recovered player')"));
            }
        }
        try (Connection reopened = open(file);
             Statement statement = reopened.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, name FROM players ORDER BY id")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt("id"));
            assertEquals("Saved player", result.getString("name"));
            assertTrue(result.next());
            assertEquals(2, result.getInt("id"));
            assertEquals("Recovered player", result.getString("name"));
            assertFalse(result.next(), "Failed insert must not persist");
        }
    }

    @Test
    void failedSchemaMigrationRollsBackBackfillAndPreservesExistingRows() throws Exception {
        Path file = directory.resolve("migration-failure.db");
        try (Connection connection = open(file);
             Statement statement = connection.createStatement()) {
            Database.initializeSchema(connection);
            statement.executeUpdate("INSERT INTO players (id, name) VALUES (1, 'Saved player')");
            statement.execute("DROP INDEX ux_game_sessions_session_sync_id");
            statement.executeUpdate("""
                    INSERT INTO game_sessions (id, played_at, session_sync_id) VALUES
                    (1, '2026-01-01 10:00:00', 'duplicate'),
                    (2, '2026-01-02 10:00:00', 'duplicate'),
                    (3, '2026-01-03 10:00:00', 'temporary')
                    """);
            // Simulate legacy data needing backfill; the duplicate IDs fail later in migration.
            statement.executeUpdate("UPDATE game_sessions SET session_sync_id = NULL WHERE id = 3");

            SQLException failure = assertThrows(SQLException.class,
                    () -> Database.initializeSchema(connection));
            assertEquals(19, failure.getErrorCode() & 0xff, "Expected SQLITE_CONSTRAINT");
            assertSavedPlayer(connection);
            try (ResultSet result = statement.executeQuery(
                    "SELECT id, played_at, session_sync_id FROM game_sessions ORDER BY id")) {
                for (int id = 1; id <= 3; id++) {
                    assertTrue(result.next());
                    assertEquals(id, result.getInt("id"));
                    assertEquals("2026-01-0" + id + " 10:00:00", result.getString("played_at"));
                    assertEquals(id == 3 ? null : "duplicate", result.getString("session_sync_id"));
                }
                assertFalse(result.next());
            }

            // Fix only the conflict, then retry on the connection that saw the failure.
            statement.executeUpdate("UPDATE game_sessions SET session_sync_id = 'repaired' WHERE id = 2");
            Database.initializeSchema(connection);
        }
        try (Connection reopened = open(file);
             Statement statement = reopened.createStatement()) {
            assertSavedPlayer(reopened);
            try (ResultSet result = statement.executeQuery("""
                    SELECT COUNT(*), COUNT(DISTINCT session_sync_id) FROM game_sessions
                    WHERE length(session_sync_id) > 0
                    """)) {
                assertTrue(result.next());
                assertEquals(3, result.getInt(1));
                assertEquals(3, result.getInt(2));
            }
        }
    }

    private Connection open(Path file) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file);
    }

    private void assertSavedPlayer(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT name FROM players WHERE id = 1")) {
            assertTrue(result.next());
            assertEquals("Saved player", result.getString("name"));
            assertFalse(result.next());
        }
    }
}
