package com.triviagame.online.rooms.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.triviagame.online.StrictInteger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** RoomRetrySettings is the explicit room HTTP contract. */
public record RoomRetrySettings(
        @NotNull @JsonDeserialize(using = StrictInteger.class) @Min(0) @Max(3)
                Integer retryAllowance) {}
