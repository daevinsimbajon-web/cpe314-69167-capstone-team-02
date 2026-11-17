package com.triviagame.online;

import com.fasterxml.jackson.databind.JsonNode;
import com.triviagame.online.rooms.RoomService;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class IndependentGameplayTest extends RoomTestSupport {
    @Autowired RoomService rooms;
    record Game(Browser host,Browser one,Browser two,String code){}
    Game game(boolean reveal,int chances)throws Exception{
        Browser host=registered(username()),one=registered(username()),two=registered(username());
        var questions=List.of(Map.of("type","TRUE_FALSE","text","The Sun is a star.","options",List.of("True","False"),"correctIndex",0),Map.of("type","TRUE_FALSE","text","Mars is a star.","options",List.of("True","False"),"correctIndex",1));
        var quiz=host.post("/quizzes",Map.of("title","Independent flow fixture","description","Isolated test only","status","READY","version",0,"questions",questions));assertThat(quiz.status()).as(quiz.body().toString()).isEqualTo(201);
        var input=new HashMap<String,Object>();input.put("title","New flow");input.put("questionSource","LOCAL");input.put("quizId",quiz.body().get("quiz").get("id").asText());input.put("questionType","any");input.put("questionCount",2);input.put("secondsPerQuestion",20);input.put("maxPlayers",2);input.put("retryAllowance",chances);input.put("revealAnswers",reveal);
        var created=host.post("/rooms",input);assertThat(created.status()).as(created.body().toString()).isEqualTo(201);String code=created.body().get("code").asText();assertThat(join(one,code).status()).isEqualTo(200);assertThat(join(two,code).status()).isEqualTo(200);
        return new Game(host,one,two,code);
    }
    JsonNode view(Browser browser,String code)throws Exception{return browser.get("/rooms/"+code).body();}
    void go(Game game)throws Exception{var start=game.host.post("/rooms/"+game.code+"/start",Map.of());assertThat(start.status()).as(start.body().toString()).isEqualTo(200);assertThat(start.body().get("status").asText()).isEqualTo("COUNTDOWN");clock.advance(Duration.ofSeconds(5));rooms.tick(game.code);}
    Result answer(Browser browser,String code,JsonNode question,int index,UUID submission)throws Exception{return browser.post("/rooms/"+code+"/answers",Map.of("questionNumber",question.get("number").asInt(),"selectedIndex",index,"submissionId",submission));}
    Result decision(Browser browser,String code,UUID submission,boolean use)throws Exception{return browser.post("/rooms/"+code+"/retry",Map.of("submissionId",submission,"useChance",use));}
    @Test void chanceDecisionNeverExtendsClocksAndExpiresWithoutSpending()throws Exception{
        var g=game(true,2);go(g);var before=view(g.one,g.code);var other=view(g.two,g.code);var q=before.get("question");UUID wrong=UUID.randomUUID();
        answer(g.one,g.code,q,1-correctIndex(g.code,q),wrong);
        assertThat(java.time.Instant.parse(view(g.one,g.code).get("lastAttempt").get("decisionExpiresAt").asText())).isEqualTo(clock.instant().plusSeconds(3));
        assertThat(view(g.one,g.code).get("deadline")).isEqualTo(before.get("deadline"));
        assertThat(view(g.two,g.code).get("deadline")).isEqualTo(other.get("deadline"));
        clock.advance(Duration.ofSeconds(2));assertThat(decision(g.one,g.code,wrong,true).status()).isEqualTo(200);
        assertThat(view(g.one,g.code).get("deadline")).isEqualTo(before.get("deadline"));
        var retry=view(g.one,g.code);assertThat(retry.get("retriesRemaining").asInt()).isEqualTo(1);
        UUID wrongAgain=UUID.randomUUID();answer(g.one,g.code,retry.get("question"),1-correctIndex(g.code,q),wrongAgain);clock.advance(Duration.ofSeconds(3));rooms.tick(g.code);
        var expired=view(g.one,g.code);assertThat(expired.get("questionFinalized").asBoolean()).isTrue();assertThat(expired.get("retriesRemaining").asInt()).isEqualTo(1);
        assertThat(decision(g.one,g.code,wrongAgain,true).status()).isEqualTo(409);
    }
    @Test void completedPlayersSeeAllFinalRankingsFromHistory()throws Exception{
        var g=game(true,0);go(g);assertThat(g.host.post("/rooms/"+g.code+"/end",null).status()).isEqualTo(200);
        assertThat(g.one.get("/history").body().get(0).get("roomCode").asText()).isEqualTo(g.code);
        var results=g.one.get("/rooms/"+g.code);assertThat(results.status()).isEqualTo(200);assertThat(results.body().get("leaderboard")).hasSize(2);
        assertThat(g.one.post("/rooms/"+g.code+"/leave",null).status()).isEqualTo(204);
        assertThat(g.one.get("/rooms/"+g.code).body().get("leaderboard")).hasSize(2);
    }
    @Test void spectatorHostCanFindAndReopenFinishedGameWithoutAPlayerScore()throws Exception{
        var g=game(true,0);Browser outsider=registered(username());
        go(g);assertThat(g.host.get("/history").body()).isEmpty();
        assertThat(g.host.post("/rooms/"+g.code+"/end",null).status()).isEqualTo(200);
        var history=g.host.get("/history").body();assertThat(history).hasSize(1);
        var entry=history.get(0);assertThat(entry.get("roomCode").asText()).isEqualTo(g.code);
        assertThat(entry.get("hosted").asBoolean()).isTrue();assertThat(entry.get("points").isNull()).isTrue();assertThat(entry.get("rank").isNull()).isTrue();
        assertThat(g.host.get("/me").body().get("statistics").get("gamesPlayed").asInt()).isZero();
        assertThat(g.host.post("/rooms/"+g.code+"/leave",null).status()).isEqualTo(204);
        var results=g.host.get("/rooms/"+g.code);assertThat(results.status()).isEqualTo(200);assertThat(results.body().get("leaderboard")).hasSize(2);
        assertThat(g.host.post("/rooms/"+g.code+"/leave",null).status()).isEqualTo(204);
        assertThat(g.one.get("/history").body().get(0).get("hosted").asBoolean()).isFalse();
        assertThat(outsider.get("/history").body()).isEmpty();assertThat(outsider.get("/rooms/"+g.code).status()).isEqualTo(403);
    }
    @Test void countdownIsPersistedHostCannotPlayAndQuestionTimeStartsAtGo()throws Exception{
        var g=game(true,2);var start=g.host.post("/rooms/"+g.code+"/start",Map.of());assertThat(start.body().get("status").asText()).isEqualTo("COUNTDOWN");var before=view(g.one,g.code);String starts=before.get("startsAt").asText();assertThat(before.get("question").isNull()).isTrue();
        assertThat(g.one.post("/rooms/"+g.code+"/answers",Map.of("questionNumber",1,"selectedIndex",0)).status()).isEqualTo(409);
        clock.advance(Duration.ofSeconds(2));assertThat(view(g.two,g.code).get("startsAt").asText()).isEqualTo(starts);clock.advance(Duration.ofSeconds(3));rooms.tick(g.code);
        var playing=view(g.one,g.code);assertThat(Duration.between(java.time.Instant.parse(playing.get("startsAt").asText()),java.time.Instant.parse(playing.get("deadline").asText()))).isEqualTo(Duration.ofSeconds(20));
        assertThat(g.host.post("/rooms/"+g.code+"/answers",Map.of("questionNumber",1,"selectedIndex",0)).status()).isEqualTo(403);assertThat(view(g.host,g.code).get("question").isNull()).isTrue();assertThat(view(g.host,g.code).get("leaderboard")).hasSize(2);assertThat(view(g.one,g.code).get("leaderboard")).hasSize(1);assertThat(view(g.one,g.code).get("playerProgress")).isEmpty();
    }
    @Test void retriesAreOptionalIdempotentDoNotExtendTimeAndPlayersAdvanceIndependently()throws Exception{
        var g=game(true,2);go(g);var initial=view(g.one,g.code);var q=initial.get("question");int correct=correctIndex(g.code,q);UUID wrong=UUID.randomUUID();
        assertThat(answer(g.one,g.code,q,1-correct,wrong).body().get("outcome").asText()).isEqualTo("RETRY");var pending=view(g.one,g.code);assertThat(pending.get("retriesRemaining").asInt()).isEqualTo(2);assertThat(pending.get("question").get("correctAnswer").isNull()).isTrue();assertThat(answer(g.one,g.code,q,correct,UUID.randomUUID()).status()).isEqualTo(409);
        assertThat(decision(g.one,g.code,wrong,true).status()).isEqualTo(200);assertThat(decision(g.one,g.code,wrong,true).status()).isEqualTo(200);var retried=view(g.one,g.code);assertThat(retried.get("retriesRemaining").asInt()).isEqualTo(1);assertThat(retried.get("deadline")).isEqualTo(initial.get("deadline"));assertThat(retried.get("submittedIndex").isNull()).isTrue();
        UUID success=UUID.randomUUID();assertThat(answer(g.one,g.code,q,correct,success).body().get("awardedPoints").asInt()).isEqualTo(7);assertThat(answer(g.one,g.code,q,correct,success).body().get("awardedPoints").asInt()).isEqualTo(7);assertThat(view(g.one,g.code).get("personalScore").asInt()).isEqualTo(7);
        clock.advance(Duration.ofMillis(1700));rooms.tick(g.code);assertThat(view(g.one,g.code).get("question").get("number").asInt()).isEqualTo(2);assertThat(view(g.two,g.code).get("question").get("number").asInt()).isEqualTo(1);
        var second=view(g.one,g.code).get("question");UUID declined=UUID.randomUUID();answer(g.one,g.code,second,1-correctIndex(g.code,second),declined);assertThat(decision(g.one,g.code,declined,false).status()).isEqualTo(200);assertThat(view(g.one,g.code).get("retriesRemaining").asInt()).isEqualTo(1);assertThat(view(g.one,g.code).get("questionFinalized").asBoolean()).isTrue();assertThat(view(g.one,g.code).get("question").get("correctAnswer").isNull()).isFalse();
        clock.advance(Duration.ofMillis(1700));rooms.tick(g.code);assertThat(view(g.one,g.code).get("playerCompleted").asBoolean()).isTrue();assertThat(view(g.two,g.code).get("question").get("number").asInt()).isEqualTo(1);
    }
    @Test void revealOffTimeoutAndZeroChancesNeverLeakSolutionOrEliminatePlayer()throws Exception{
        var g=game(false,1);go(g);var q=view(g.one,g.code).get("question");UUID attempt=UUID.randomUUID();answer(g.one,g.code,q,1-correctIndex(g.code,q),attempt);
        clock.advance(Duration.ofSeconds(20));rooms.tick(g.code);assertThat(decision(g.one,g.code,attempt,true).status()).isEqualTo(409);var timeout=view(g.one,g.code);assertThat(timeout.get("retriesRemaining").asInt()).isEqualTo(1);assertThat(timeout.get("questionFinalized").asBoolean()).isTrue();assertThat(timeout.get("question").get("correctAnswer").isNull()).isTrue();
        clock.advance(Duration.ofMillis(1700));rooms.tick(g.code);q=view(g.one,g.code).get("question");UUID retry=UUID.randomUUID();answer(g.one,g.code,q,1-correctIndex(g.code,q),retry);decision(g.one,g.code,retry,true);var correct=answer(g.one,g.code,q,correctIndex(g.code,q),UUID.randomUUID());assertThat(correct.status()).isEqualTo(202);assertThat(view(g.one,g.code).get("retriesRemaining").asInt()).isZero();assertThat(view(g.one,g.code).get("question").get("correctAnswer").isNull()).isTrue();
    }
    @Test void exhaustedChancesNeverPreventAnsweringTheNextQuestion()throws Exception{
        var g=game(true,1);go(g);var q=view(g.one,g.code).get("question");UUID first=UUID.randomUUID();int correct=correctIndex(g.code,q);
        answer(g.one,g.code,q,1-correct,first);decision(g.one,g.code,first,true);assertThat(view(g.one,g.code).get("retriesRemaining").asInt()).isZero();
        assertThat(answer(g.one,g.code,q,1-correct,UUID.randomUUID()).body().get("outcome").asText()).isEqualTo("INCORRECT");
        clock.advance(Duration.ofMillis(1700));rooms.tick(g.code);var next=view(g.one,g.code).get("question");assertThat(next.get("number").asInt()).isEqualTo(2);assertThat(answer(g.one,g.code,next,correctIndex(g.code,next),UUID.randomUUID()).body().get("awardedPoints").asInt()).isEqualTo(10);
    }
    @Test void concurrentDuplicateSubmissionsCannotAwardPointsTwice()throws Exception{
        var g=game(true,0);go(g);var q=view(g.one,g.code).get("question");int correct=correctIndex(g.code,q);UUID submission=UUID.randomUUID();
        java.util.function.Supplier<Result> submit=()->{try{return answer(g.one,g.code,q,correct,submission);}catch(Exception failure){throw new RuntimeException(failure);}};
        var first=java.util.concurrent.CompletableFuture.supplyAsync(submit);var second=java.util.concurrent.CompletableFuture.supplyAsync(submit);
        assertThat(first.get().status()).isEqualTo(202);assertThat(second.get().status()).isEqualTo(202);assertThat(view(g.one,g.code).get("personalScore").asInt()).isEqualTo(10);
    }
}
