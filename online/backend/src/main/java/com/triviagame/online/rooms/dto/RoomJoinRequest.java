package com.triviagame.online.rooms.dto;

import jakarta.validation.constraints.Size;

/** Optional password used to join an existing room. */
public record RoomJoinRequest(@Size(max = 64) String password) {}
