package com.triviagame.online.rooms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** RoomAnswerRequest is the explicit room HTTP contract. */
public record RoomAnswerRequest(
        @Min(1) int questionNumber,
        @Min(0) @Max(10) int selectedIndex,
        @NotNull UUID submissionId) {}
