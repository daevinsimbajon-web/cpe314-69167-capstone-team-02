package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persists stable player identities separately from started game sessions. */
public final class SessionPlayerDao {
    public record PlayerSetup(String name, int secondsPerQuestion, Integer playerId) {
        public PlayerSetup(String name, int secondsPerQuestion) { this(name, secondsPerQuestion, null); }
        public PlayerSetup {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Player name required");
            name = name.trim();
            if (secondsPerQuestion < 5 || secondsPerQuestion > 300) throw new IllegalArgumentException("Each player needs 5–300 seconds per question");
            if (playerId != null && playerId <= 0) throw new IllegalArgumentException("Invalid player ID");
        }
    }
    public record RegisteredPlayer(int playerId, String name, int secondsPerQuestion, int turnOrder) {}
    public record GameSetup(int sessionId, List<RegisteredPlayer> players) {}

    public void initialize() throws SQLException {
        Database.initializeSchema();
        try (Connection connection = Database.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS session_players (
                    session_id INTEGER NOT NULL REFERENCES game_sessions(id),
                    player_id INTEGER NOT NULL REFERENCES players(id),
                    turn_order INTEGER NOT NULL CHECK (turn_order > 0),
                    seconds_per_question INTEGER NOT NULL CHECK (seconds_per_question BETWEEN 5 AND 300),
                    PRIMARY KEY (session_id, player_id), UNIQUE (session_id, turn_order)
                )
                """);
        }
    }

    /** Resolves or creates player identities without starting a game session. */
    public List<RegisteredPlayer> preparePlayers(List<PlayerSetup> setups) throws SQLException {
        if (setups == null || setups.isEmpty()) throw new IllegalArgumentException("Add at least one player");
        if (setups.size() > 12) throw new IllegalArgumentException("Maximum 12 players per game");
        Set<Integer> identities = new HashSet<>();
        for (PlayerSetup player : setups) {
            if (player == null || player.playerId() != null && !identities.add(player.playerId()))
                throw new IllegalArgumentException("Null or duplicate player in game setup");
        }
        initialize();
        List<RegisteredPlayer> players = new ArrayList<>();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (int i = 0; i < setups.size(); i++) {
                    PlayerSetup player = setups.get(i);
                    int id = player.playerId() == null ? createPlayer(connection, player.name()) : existingPlayer(connection, player.playerId());
                    players.add(new RegisteredPlayer(id, player.name(), player.secondsPerQuestion(), i + 1));
                }
                connection.commit();
                return List.copyOf(players);
            } catch (SQLException | RuntimeException ex) { connection.rollback(); throw ex; }
        }
    }

    /** Creates a session only when an answer is about to be persisted. */
    public int createSession(List<RegisteredPlayer> players) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO game_sessions(played_at,session_sync_id) VALUES (CURRENT_TIMESTAMP,lower(hex(randomblob(16))))")) { statement.executeUpdate(); }
                int sessionId = lastInsertId(connection);
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO session_players(session_id,player_id,turn_order,seconds_per_question) VALUES (?,?,?,?)")) {
                    for (var player : players) {
                        insert.setInt(1, sessionId);insert.setInt(2, player.playerId());insert.setInt(3, player.turnOrder());insert.setInt(4, player.secondsPerQuestion());insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();return sessionId;
            } catch (SQLException | RuntimeException ex) { connection.rollback(); throw ex; }
        }
    }

    public GameSetup createGame(List<PlayerSetup> setups) throws SQLException {
        var players = preparePlayers(setups);
        return new GameSetup(createSession(players), players);
    }

    /** Removes only a newly created session whose first answer failed to persist. */
    public boolean discardNewEmptySession(int sessionId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement check = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM scores WHERE session_id=?) OR EXISTS(SELECT 1 FROM timed_answers WHERE session_id=?)")) {
                    check.setInt(1, sessionId);check.setInt(2, sessionId);
                    try (ResultSet result = check.executeQuery()) { if (!result.next() || result.getBoolean(1)) { connection.rollback();return false; } }
                }
                try (PreparedStatement remove = connection.prepareStatement("DELETE FROM session_players WHERE session_id=?")) {remove.setInt(1, sessionId);remove.executeUpdate();}
                try (PreparedStatement remove = connection.prepareStatement("DELETE FROM game_sessions WHERE id=?")) {remove.setInt(1, sessionId);if (remove.executeUpdate()!=1) {connection.rollback();return false;}}
                connection.commit();return true;
            } catch (SQLException | RuntimeException ex) {connection.rollback();throw ex;}
        }
    }

    private int existingPlayer(Connection connection, int playerId) throws SQLException {
        try (PreparedStatement find = connection.prepareStatement("SELECT id FROM players WHERE id=?")) {
            find.setInt(1, playerId);
            try (ResultSet result = find.executeQuery()) { if (result.next()) return result.getInt(1); }
        }
        throw new SQLException("Selected player no longer exists");
    }
    private int createPlayer(Connection connection, String name) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO players(name,player_sync_id) VALUES (?,lower(hex(randomblob(16))))")) {
            insert.setString(1, name);insert.executeUpdate();return lastInsertId(connection);
        }
    }
    private int lastInsertId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT last_insert_rowid()"); ResultSet result = statement.executeQuery()) {
            if (!result.next()) throw new SQLException("Cannot obtain inserted ID");
            return result.getInt(1);
        }
    }
}
