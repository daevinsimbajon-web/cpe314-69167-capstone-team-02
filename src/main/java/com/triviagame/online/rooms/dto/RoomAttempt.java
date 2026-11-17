package com.triviagame.online.rooms.dto;

import java.time.Instant;
import java.util.UUID;

/** RoomAttempt is the explicit room HTTP contract. */
public record RoomAttempt(
        UUID submissionId,
        String outcome,
        int retriesRemaining,
        Instant acceptedAt,
        int pointsAvailable,
        Integer awardedPoints,
        Instant decisionExpiresAt,
        Boolean questionTimerPaused) {
    public RoomAttempt(UUID submissionId,String outcome,int retriesRemaining,Instant acceptedAt,int pointsAvailable,Integer awardedPoints){
        this(submissionId,outcome,retriesRemaining,acceptedAt,pointsAvailable,awardedPoints,null,null);
    }
}
