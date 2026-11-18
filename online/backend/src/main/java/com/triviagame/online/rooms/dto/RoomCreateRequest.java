package com.triviagame.online.rooms.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.triviagame.online.StrictInteger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** RoomCreateRequest is the explicit room HTTP contract. */
public record RoomCreateRequest(
        @NotBlank @Size(max = 80) String title,
        Long categoryId,
        String difficulty,
        @NotNull @Pattern(regexp = "any|multiple|boolean")
                String questionType,
        @JsonDeserialize(using = StrictInteger.class) @Min(1) @Max(50) int questionCount,
        @JsonDeserialize(using = StrictInteger.class) @Min(5) @Max(120) int secondsPerQuestion,
        @JsonDeserialize(using = StrictInteger.class) @Min(2) @Max(50) int maxPlayers,
        @Size(max = 64) String password,
        @JsonDeserialize(using = StrictInteger.RetryAllowance.class) @Min(0) @Max(3)
                Integer retryAllowance,
        @Pattern(regexp = "OPENTDB|LOCAL") String questionSource,
        @JsonDeserialize(using = StrictInteger.class) @Min(1) Integer openTdbCategoryId,
        UUID quizId,
        Boolean revealAnswers) {}
