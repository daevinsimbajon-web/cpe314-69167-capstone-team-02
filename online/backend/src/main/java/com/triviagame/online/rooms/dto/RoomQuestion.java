package com.triviagame.online.rooms.dto;

import java.util.List;

/** RoomQuestion is the explicit room HTTP contract. */
public record RoomQuestion(
        int number,
        String text,
        List<String> choices,
        String difficulty,
        String type,
        String correctAnswer) {}
