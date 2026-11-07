
package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SessionPlayerDao {

    public record PlayerSetup(
            String name,
            int secondsPerQuestion) {

        public PlayerSetup {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "Player name required"
                );
            }

            name = name.trim();

            if (secondsPerQuestion < 5
                    || secondsPerQuestion > 300) {
                throw new IllegalArgumentException(
                        "Each player needs 5–300 seconds per question"
                );
            }
        }
    }

    public record RegisteredPlayer(
            int playerId,
            String name,
            int secondsPerQuestion,
            int turnOrder) {
    }

    public record GameSetup(
            int sessionId,
            List<RegisteredPlayer> players) {
    }

    public void initialize() throws SQLException {
        Database.initializeSchema();

        try (
            Connection c = Database.getConnection();
            Statement st = c.createStatement()
        ) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS session_players (
                    session_id INTEGER NOT NULL
                        REFERENCES game_sessions(id),
                    player_id INTEGER NOT NULL
                        REFERENCES players(id),
                    turn_order INTEGER NOT NULL
                        CHECK (turn_order > 0),
                    seconds_per_question INTEGER NOT NULL
                        CHECK (seconds_per_question BETWEEN 5 AND 300),
                    PRIMARY KEY (session_id, player_id),
                    UNIQUE (session_id, turn_order)
                )
                """);
        }
    }

    public GameSetup createGame(
            List<PlayerSetup> setups)
            throws SQLException {

        if (setups == null || setups.isEmpty()) {
            throw new IllegalArgumentException(
                    "Add at least one player"
            );
        }

        if (setups.size() > 12) {
            throw new IllegalArgumentException(
                    "Maximum 12 players per game"
            );
        }

        Set<String> names = new HashSet<>();

        for (PlayerSetup player : setups) {
            if (player == null || !names.add(
                    player.name().toLowerCase(
                            Locale.ROOT
                    ))) {

                throw new IllegalArgumentException(
                        "Null or duplicate player in game setup"
                );
            }
        }

        initialize();

        List<RegisteredPlayer> participants =
                new ArrayList<>();

        try (
            Connection connection =
                    Database.getConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                int sessionId;

                try (
                    PreparedStatement ps =
                            connection.prepareStatement(
                                    "INSERT INTO game_sessions(played_at) "
                                    + "VALUES (CURRENT_TIMESTAMP)"
                            )
                ) {
                    ps.executeUpdate();

                    sessionId =
                            lastInsertId(connection);
                }

                for (int i = 0;
                        i < setups.size();
                        i++) {

                    PlayerSetup player =
                            setups.get(i);

                    int playerId =
                            findOrCreatePlayer(
                                    connection,
                                    player.name()
                            );

                    try (
                        PreparedStatement ps =
                                connection.prepareStatement("""
                                    INSERT INTO session_players(
                                        session_id,
                                        player_id,
                                        turn_order,
                                        seconds_per_question
                                    ) VALUES (?, ?, ?, ?)
                                    """)
                    ) {
                        ps.setInt(1, sessionId);
                        ps.setInt(2, playerId);
                        ps.setInt(3, i + 1);
                        ps.setInt(
                                4,
                                player.secondsPerQuestion()
                        );

                        ps.executeUpdate();
                    }

                    participants.add(
                            new RegisteredPlayer(
                                    playerId,
                                    player.name(),
                                    player.secondsPerQuestion(),
                                    i + 1
                            )
                    );
                }

                connection.commit();

                return new GameSetup(
                        sessionId,
                        List.copyOf(participants)
                );

            } catch (SQLException
                    | RuntimeException ex) {

                connection.rollback();
                throw ex;
            }
        }
    }

    private int findOrCreatePlayer(
            Connection connection,
            String name) throws SQLException {

        try (
            PreparedStatement find =
                    connection.prepareStatement("""
                        SELECT id FROM players
                        WHERE name = ? COLLATE NOCASE
                        ORDER BY id ASC LIMIT 1
                        """)
        ) {
            find.setString(1, name);

            try (
                ResultSet rs = find.executeQuery()
            ) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        try (
            PreparedStatement insert =
                    connection.prepareStatement(
                            "INSERT INTO players(name) VALUES (?)"
                    )
        ) {
            insert.setString(1, name);
            insert.executeUpdate();

            return lastInsertId(connection);
        }
    }

    private int lastInsertId(
            Connection connection)
            throws SQLException {

        try (
            PreparedStatement ps =
                    connection.prepareStatement(
                            "SELECT last_insert_rowid()"
                    );
            ResultSet rs =
                    ps.executeQuery()
        ) {
            if (!rs.next()) {
                throw new SQLException(
                        "Cannot obtain inserted ID"
                );
            }

            return rs.getInt(1);
        }
    }
}
