
package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerDaoTest {

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    @Test
    void testInsertSelectAndDeletePlayer()
            throws SQLException {

        PlayerDao dao = new PlayerDao();

        String name = "TestPlayer_" + UUID.randomUUID();

        // INSERT
        int playerId = dao.addPlayer(name);

        assertTrue(playerId > 0);

        try {
            // SELECT
            PlayerDao.PlayerRecord player =
                dao.getPlayerById(playerId).orElseThrow();

            assertEquals(playerId, player.id());
            assertEquals(name, player.name());

            // Confirm the player appears in the list
            assertTrue(
                dao.getAllPlayers().stream()
                    .anyMatch(p -> p.id() == playerId)
            );

        } finally {
            // Clean up the test record
            assertTrue(dao.deletePlayer(playerId));
        }

        // Confirm deletion
        assertTrue(
            dao.getPlayerById(playerId).isEmpty()
        );
    }

    @Test
    void testEmptyPlayerName() {

        PlayerDao dao = new PlayerDao();

        assertThrows(
            IllegalArgumentException.class,
            () -> dao.addPlayer("")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> dao.addPlayer("   ")
        );
    }
}
