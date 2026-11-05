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

            if (secondsPerQuestion < 5 ||
                    secondsPerQuestion > 300) {
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

    public GameSetup createGame(List<PlayerSetup> setups)
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

        for (PlayerSetup p : setups) {
            if (!names.add(
                p.name().toLowerCase(Locale.ROOT)
            )) {
                throw new IllegalArgumentException(
                    "Duplicate player name: " + p.name()
                );
            }
        }

        initialize();

        List<RegisteredPlayer> created =
                new ArrayList<>();

        try (Connection c = Database.getConnection()) {

            c.setAutoCommit(false);

            try {
                int sessionId;

                try (
                    PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO game_sessions (played_at) "
                        + "VALUES (CURRENT_TIMESTAMP)"
                    )
                ) {
                    ps.executeUpdate();
                    sessionId = lastInsertId(c);
                }

                for (int i = 0; i < setups.size(); i++) {

                    PlayerSetup p = setups.get(i);
                    int playerId;

                    try (
                        PreparedStatement ps =
                            c.prepareStatement(
                                "INSERT INTO players(name) VALUES (?)"
                            )
                    ) {
                        ps.setString(1, p.name());
                        ps.executeUpdate();
                        playerId = lastInsertId(c);
                    }

                    try (
                        PreparedStatement ps =
                            c.prepareStatement("""
                                INSERT INTO session_players(
                                    session_id,
                                    player_id,
                                    turn_order,
                                    seconds_per_question
                                )
                                VALUES (?, ?, ?, ?)
                                """)
                    ) {
                        ps.setInt(1, sessionId);
                        ps.setInt(2, playerId);
                        ps.setInt(3, i + 1);
                        ps.setInt(4, p.secondsPerQuestion());
                        ps.executeUpdate();
                    }

                    created.add(new RegisteredPlayer(
                        playerId,
                        p.name(),
                        p.secondsPerQuestion(),
                        i + 1
                    ));
                }

                c.commit();

                return new GameSetup(
                    sessionId,
                    List.copyOf(created)
                );

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private int lastInsertId(Connection c)
            throws SQLException {

        try (
            PreparedStatement ps =
                c.prepareStatement(
                    "SELECT last_insert_rowid()"
                );
            ResultSet rs = ps.executeQuery()
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