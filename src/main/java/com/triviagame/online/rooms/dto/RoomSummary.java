package com.triviagame.online.rooms.dto;

import java.time.Instant;

/** RoomSummary is the explicit room HTTP contract. */
public record RoomSummary(
        String code, String title, String status, Instant createdAt, boolean host) {}
