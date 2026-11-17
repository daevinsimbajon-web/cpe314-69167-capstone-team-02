package com.triviagame.m2;

import static org.junit.jupiter.api.Assertions.*;

import com.triviagame.db.SessionPlayerDao.PlayerSetup;
import org.junit.jupiter.api.Test;

class PlayerSetupTest {

    @Test
    void recordsCustomSeconds() {
        var player = new PlayerSetup("  Alex  ", 45);

        assertEquals("Alex", player.name());
        assertEquals(45, player.secondsPerQuestion());
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new PlayerSetup(" ", 30)
        );
    }

    @Test
    void rejectsInvalidSeconds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new PlayerSetup("Alex", 0)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PlayerSetup("Alex", 301)
        );
    }
}