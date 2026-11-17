package com.triviagame.online.analytics;

import java.time.Instant;
import java.util.List;
import com.triviagame.online.auth.AvatarConfig;

/** Explicit allowlist: never serialize account entities, answers, choices, or sessions. */
public record AnalyticsExport(String format,int schemaVersion,Instant exportedAt,Player player,List<Game> games,IdentityProof identityProof) {
    public AnalyticsExport(String format,int schemaVersion,Instant exportedAt,Player player,List<Game> games) {
        this(format,schemaVersion,exportedAt,player,games,null);
    }
    public record IdentityProof(String accountId,String signature) { }
    public record Player(String exportPlayerId,String displayName,AvatarConfig avatar,String username) {
        public Player(String id,String name,AvatarConfig avatar){this(id,name,avatar,null);}
    }
    public record Question(String questionId,String sourceQuestionId,String questionLabel,String category,String difficulty,
            String questionType,Boolean firstTryCorrect,boolean finalCorrect,Integer attemptCount,Integer retriesUsed,
            Long elapsedMs,int pointsEarned,int maxPossiblePoints,boolean timedOut) { }
    public record Game(String gameId,Instant playedAt,int pointsEarned,int maxPossiblePoints,boolean won,
            String scoringVersion,int presentedQuestions,boolean questionsComplete,List<Question> questions) { }
}
