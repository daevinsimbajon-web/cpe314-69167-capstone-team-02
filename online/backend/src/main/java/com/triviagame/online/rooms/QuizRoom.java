package com.triviagame.online.rooms;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="quiz_rooms")
public class QuizRoom {
    @Id UUID id;
    @Column(nullable=false,length=8,unique=true) String code;
    @Column(name="host_id",nullable=false) UUID hostId;
    @Column(nullable=false,length=80) String title;
    @Column(nullable=false,length=16) String status;
    @Column(name="category_id") Long categoryId;
    @Column(name="question_source",nullable=false) String questionSource="LOCAL";
    @Column(name="opentdb_category_id") Integer openTdbCategoryId;
    @Column(name="custom_quiz_id") UUID customQuizId;
    @Column(name="custom_quiz_version") Integer customQuizVersion;
    @Column(nullable=false,length=10) String difficulty;
    @Column(name="question_type",nullable=false,length=20) String questionType;
    @Column(name="question_count",nullable=false) int questionCount;
    @Column(name="seconds_per_question",nullable=false) int secondsPerQuestion;
    @Column(name="max_players",nullable=false) int maxPlayers;
    @Column(name="retry_allowance",nullable=false) int retryAllowance;
    @Column(name="scoring_version",nullable=false) String scoringVersion=ScoringService.CURRENT;
    @Column(name="question_started_at") Instant questionStartedAt;
    @Column(name="password_hash",length=100) String passwordHash;
    @Column(name="current_question",nullable=false) int currentQuestion=-1;
    Instant deadline;
    @Column(name="independent_flow",nullable=false) boolean independentFlow;
    @Column(name="reveal_answers",nullable=false) boolean revealAnswers=true;
    @Column(name="starts_at") Instant startsAt;
    @Column(name="created_at",nullable=false) Instant createdAt;
    @Column(name="expires_at",nullable=false) Instant expiresAt;
    protected QuizRoom() { }
}
