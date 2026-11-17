package com.triviagame.online.rooms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RoomView is the explicit room HTTP contract. */
public record RoomView(
        String code,
        String title,
        String status,
        UUID hostId,
        String invitationUrl,
        int maxPlayers,
        int questionCount,
        int secondsPerQuestion,
        Instant deadline,
        Instant serverTime,
        List<RoomParticipant> participants,
        List<RoomRanking> leaderboard,
        RoomQuestion question,
        Integer submittedIndex,
        int retryAllowance,
        int retriesRemaining,
        boolean questionFinalized,
        RoomAttempt lastAttempt,
        int pointsAvailable,
        Integer awardedPoints,
        String scoringVersion,
        String questionSource,
        Integer openTdbCategoryId,
        String difficulty,
        String questionType,
        UUID quizId,
        Integer quizVersion,
        boolean independentFlow,
        boolean revealAnswers,
        Instant startsAt,
        Instant feedbackUntil,
        boolean playerCompleted,
        int personalScore,
        List<PlayerProgress> playerProgress) {
    public record PlayerProgress(UUID id,int questionNumber,boolean completed,int points,int retriesRemaining,int answeredQuestions,int correctAnswers) {}
}
