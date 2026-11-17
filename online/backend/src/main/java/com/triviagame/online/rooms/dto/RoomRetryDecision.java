package com.triviagame.online.rooms.dto;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
public record RoomRetryDecision(@NotNull UUID submissionId,@NotNull Boolean useChance) {}
