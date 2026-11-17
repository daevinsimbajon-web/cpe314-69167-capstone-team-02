package com.triviagame.online.rooms.dto;

import com.triviagame.online.auth.AvatarConfig;

import java.util.UUID;

/** RoomRanking is the explicit room HTTP contract. */
public record RoomRanking(
        UUID id,
        String displayName,
        int points,
        int correctCount,
        int rank,
        AvatarConfig avatar,
        boolean forfeited,String username) {
    public RoomRanking(UUID id,String displayName,int points,int correctCount,int rank,AvatarConfig avatar,boolean forfeited){this(id,displayName,points,correctCount,rank,avatar,forfeited,null);}
}
