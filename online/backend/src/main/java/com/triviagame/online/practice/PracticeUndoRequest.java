package com.triviagame.online.practice;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.triviagame.online.StrictInteger;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Reverses one scored practice answer at the supplied version. */
public record PracticeUndoRequest(
        @NotNull @Min(0) @JsonDeserialize(using = StrictInteger.class) Integer version,
        @NotNull UUID actionId) {}
