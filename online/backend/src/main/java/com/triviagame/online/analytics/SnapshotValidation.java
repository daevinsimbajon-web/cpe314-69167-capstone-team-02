package com.triviagame.online.analytics;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.triviagame.online.analytics.AnalyticsExport.*;

/** Validates the actual scoreboard, including scoring and question coverage, before any write. */
@Component
public class SnapshotValidation {
    private final ObjectMapper json;
    public SnapshotValidation(ObjectMapper json){this.json=json;}
    public AnalyticsExport read(JsonNode root) {
        check(root.isObject()&&root.path("format").asText().equals("hootly.analytics")&&root.path("schemaVersion").isIntegralNumber()&&root.path("schemaVersion").asInt()==1,"Choose a version 1 Hootly analytics export.");
        text(root.path("player").get("exportPlayerId"),120);text(root.path("player").get("displayName"),100);
        JsonNode username=root.path("player").get("username");if(username!=null&&!username.isNull())check(username.isTextual()&&username.asText().matches("[A-Za-z0-9_]{3,24}"),"Invalid username.");
        timestamp(root.get("exportedAt"));check(root.path("games").isArray()&&root.path("games").size()<=5000,"Choose at most 5,000 historical games.");
        for(JsonNode game:root.path("games")) {
            text(game.get("gameId"),120);timestamp(game.get("playedAt"));number(game,"pointsEarned",50_000_000);number(game,"maxPossiblePoints",50_000_000);bool(game,"won");
            check(game.path("questions").isArray()&&game.path("questions").size()<=50,"Each game must contain at most 50 question records.");
            Set<String> ids=new HashSet<>();int points=0,maximum=0,retries=0;String version=game.path("scoringVersion").asText("unknown");check(version.length()<=30,"Invalid scoring version.");
            for(JsonNode q:game.path("questions")) {
                text(q.get("questionId"),120);check(ids.add(q.get("questionId").asText()),"Repeated question ID.");text(q.get("category"),200);
                for(String field:List.of("sourceQuestionId","questionLabel","difficulty","questionType"))if(q.hasNonNull(field))text(q.get(field),field.equals("questionLabel")?10000:field.equals("sourceQuestionId")?120:30);
                int p=number(q,"pointsEarned",1_000_000),m=number(q,"maxPossiblePoints",1_000_000);boolean correct=bool(q,"finalCorrect"),timeout=bool(q,"timedOut");
                check(m>0&&p<=m&&(correct||p==0)&&!(correct&&timeout),"Impossible question points or correctness.");
                Integer attempts=optional(q,"attemptCount",11),retry=optional(q,"retriesUsed",10);optional(q,"elapsedMs",86_400_000);
                Boolean first=q.hasNonNull("firstTryCorrect")?bool(q,"firstTryCorrect"):null;
                if(attempts!=null){check(attempts!=0||timeout&&first==null,"Invalid timeout attempts.");check(attempts==0||first!=null,"Missing first-try result.");
                    check(retry==null||retry==attempts-(timeout?0:1),"Retries do not match attempts.");check(!Boolean.TRUE.equals(first)||attempts==1&&correct,"Conflicting first-try result.");}
                if(version.equals("retry-v2"))check(m==10&&retry!=null&&retry<=3&&attempts!=null&&p==(correct?new int[]{10,7,4,2}[retry]:0),"Question points do not match retry-v2 scoring.");
                if(List.of("retry-v1","legacy-v1").contains(version))check(p==(correct?m:0),"Question points do not match historical scoring.");
                points+=p;maximum+=m;retries+=retry==null?0:retry;
            }
            check(points==game.get("pointsEarned").intValue()&&maximum<=game.get("maxPossiblePoints").intValue()&&points<=game.get("maxPossiblePoints").intValue(),"Game totals do not match question records.");
            check(retries<=(version.equals("retry-v2")?3:10),"Retry usage exceeds this ruleset.");
            int presented=game.hasNonNull("presentedQuestions")?number(game,"presentedQuestions",50):ids.size();
            boolean complete=game.hasNonNull("questionsComplete")&&bool(game,"questionsComplete");
            check(presented>=ids.size()&&(!complete||presented==ids.size()&&maximum==game.get("maxPossiblePoints").intValue()),"Question coverage conflicts with game totals.");
        }
        try {
            var reader=json.readerFor(AnalyticsExport.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            AnalyticsExport parsed=reader.readValue(root);check(parsed.player().avatar()==null||parsed.player().avatar().valid(),"Invalid player avatar.");var games=new ArrayList<Game>();
            for(int i=0;i<parsed.games().size();i++) {
                Game g=parsed.games().get(i);JsonNode raw=root.get("games").get(i);
                games.add(new Game(g.gameId(),g.playedAt(),g.pointsEarned(),g.maxPossiblePoints(),g.won(),g.scoringVersion()==null?"unknown":g.scoringVersion(),raw.hasNonNull("presentedQuestions")?g.presentedQuestions():g.questions().size(),g.questionsComplete(),g.questions()));
            }
            return new AnalyticsExport(parsed.format(),parsed.schemaVersion(),parsed.exportedAt(),parsed.player(),games);
        }catch(Exception invalid){throw new IllegalArgumentException("Invalid historical scoreboard records.",invalid);}
    }
    public String gameJson(Game game) {
        var sorted=new ArrayList<>(game.questions());sorted.sort(Comparator.comparing(Question::questionId));
        return write(new Game(game.gameId(),game.playedAt(),game.pointsEarned(),game.maxPossiblePoints(),game.won(),game.scoringVersion(),game.presentedQuestions(),game.questionsComplete(),sorted));
    }
    public String write(Object value){try{return json.writeValueAsString(value);}catch(Exception invalid){throw new IllegalArgumentException("Invalid statistics data.",invalid);}}
    private static int number(JsonNode q,String field,int max){JsonNode n=q.get(field);check(n!=null&&n.isIntegralNumber()&&n.canConvertToInt()&&n.intValue()>=0&&n.intValue()<=max,"Invalid "+field+".");return n.intValue();}
    private static Integer optional(JsonNode q,String field,int max){return q.hasNonNull(field)?number(q,field,max):null;}
    private static boolean bool(JsonNode q,String field){check(q.path(field).isBoolean(),"Invalid "+field+".");return q.get(field).booleanValue();}
    private static void text(JsonNode n,int max){check(n!=null&&n.isTextual()&&!n.asText().isBlank()&&n.asText().length()<=max,"Invalid text field.");}
    private static void timestamp(JsonNode n){text(n,60);try{OffsetDateTime.parse(n.asText());}catch(Exception invalid){throw new IllegalArgumentException("Use a valid date with a time zone.");}}
    private static void check(boolean valid,String message){if(!valid)throw new IllegalArgumentException(message);}
}
