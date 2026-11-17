package com.triviagame.online.practice;

import java.util.UUID;

/** Persistence result, including an optimistic version and the last idempotency key. */
public record PracticeSession(UUID id, int version, UUID lastAction, PracticeState state) {}
