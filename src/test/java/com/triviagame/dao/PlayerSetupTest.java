package com.triviagame.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.triviagame.model.GamePlayer.Setup;
import org.junit.jupiter.api.Test;

class PlayerSetupTest {

    @Test
    void recordsCustomSeconds() {
        var player = new Setup("  Alex  ", 45);

        assertEquals("Alex", player.name());
        assertEquals(45, player.secondsPerQuestion());
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Setup(" ", 30)
        );
    }

    @Test
    void rejectsInvalidSeconds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Setup("Alex", 0)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new Setup("Alex", 301)
        );
    }
}