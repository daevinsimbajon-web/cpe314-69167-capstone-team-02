
package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerDao {

    // Represents a player record from SQLite
    public record PlayerRecord(int id, String name) {
    }

    // INSERT: Save a new player and return the new ID
    public int addPlayer(String name) throws SQLException {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Player name cannot be empty"
            );
        }

        String sql = """
            INSERT INTO players (name)
            VALUES (?)
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, name.trim());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                    "Failed to insert player"
                );
            }

            // Get the newly generated player ID
            String idSql =
                "SELECT last_insert_rowid() AS id";

            try (PreparedStatement idStatement =
                     connection.prepareStatement(idSql);
                 ResultSet result =
                     idStatement.executeQuery()) {

                if (result.next()) {
                    return result.getInt("id");
                }

                throw new SQLException(
                    "Player ID could not be retrieved"
                );
            }
        }
    }

    // SELECT: Find one player by ID
    public Optional<PlayerRecord> getPlayerById(int id)
            throws SQLException {

        String sql = """
            SELECT id, name
            FROM players
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet result =
                     statement.executeQuery()) {

                if (result.next()) {
                    PlayerRecord player = new PlayerRecord(
                        result.getInt("id"),
                        result.getString("name")
                    );

                    return Optional.of(player);
                }
            }
        }

        return Optional.empty();
    }

    // SELECT: Retrieve all registered players
    public List<PlayerRecord> getAllPlayers()
            throws SQLException {

        List<PlayerRecord> players = new ArrayList<>();

        String sql = """
            SELECT id, name
            FROM players
            ORDER BY id ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result =
                     statement.executeQuery()) {

            while (result.next()) {
                players.add(new PlayerRecord(
                    result.getInt("id"),
                    result.getString("name")
                ));
            }
        }

        return players;
    }

    // DELETE: Remove a player by ID
    public boolean deletePlayer(int id)
            throws SQLException {

        String sql = """
            DELETE FROM players
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            return statement.executeUpdate() > 0;
        }
    }
}
