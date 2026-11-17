package com.triviagame.online.rooms.dto;

import com.triviagame.online.auth.AvatarConfig;

import java.util.UUID;

/** RoomParticipant is the explicit room HTTP contract. */
public record RoomParticipant(
        UUID id,
        String displayName,
        String role,
        boolean connected,
        boolean left,
        AvatarConfig avatar,String username) {
    public RoomParticipant(UUID id,String displayName,String role,boolean connected,boolean left,AvatarConfig avatar){this(id,displayName,role,connected,left,avatar,null);}
}
