package com.triviagame.online.bank;

import java.util.UUID;

/** One owner-scoped node in a custom category tree. */
public record CategoryBranch(UUID id, String name, UUID parentId) {}
