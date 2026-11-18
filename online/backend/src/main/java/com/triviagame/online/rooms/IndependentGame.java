package com.triviagame.online.rooms;

import com.triviagame.online.DatabaseTimes;
import com.triviagame.online.bank.QuestionBank;
import com.triviagame.online.rooms.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** New room flow. Every operation runs under RoomService's transaction and room lock. */
@Service
public class IndependentGame {
    private static final Duration CHANCE_WINDOW=Duration.ofSeconds(5);
    // Allow a bounded transport delay for a click made just before the displayed deadline.
    private static final Duration ANSWER_DELIVERY_GRACE=Duration.ofSeconds(1);
    private final JdbcTemplate jdbc; private final RoomDataRepository data; private final QuestionBank bank;
    private final ScoringService scoring; private final Clock clock;
    public IndependentGame(JdbcTemplate jdbc,RoomDataRepository data,QuestionBank bank,ScoringService scoring,Clock clock){this.jdbc=jdbc;this.data=data;this.bank=bank;this.scoring=scoring;this.clock=clock;}
    private record Progress(UUID user,int ordinal,Instant started,Instant deadline,Instant feedback,Instant completed) {}
    private List<Progress> players(QuizRoom r){return jdbc.query("SELECT * FROM room_player_progress WHERE room_id=? ORDER BY user_id",(rs,n)->new Progress(rs.getObject("user_id",UUID.class),rs.getInt("ordinal"),rs.getObject("question_started_at",OffsetDateTime.class).toInstant(),rs.getObject("deadline",OffsetDateTime.class).toInstant(),instant(rs.getObject("feedback_until")),instant(rs.getObject("completed_at"))),r.id);}
    private Progress player(QuizRoom r,UUID user){return players(r).stream().filter(p->p.user.equals(user)).findFirst().orElseThrow(()->denied("The host watches this game and cannot submit answers."));}
    private static Instant instant(Object value){return value==null?null:DatabaseTimes.instant(value);}
    private static OffsetDateTime at(Instant value){return value.atOffset(ZoneOffset.UTC);}
    private Map<String,Object> question(QuizRoom r,Progress p){return data.current(r.id,p.ordinal);}
    private int balance(QuizRoom r,UUID user){return data.balances(r.id,user).stream().findFirst().orElse(0);}
    private int retries(UUID question,UUID user){return jdbc.queryForObject("SELECT COUNT(*) FROM room_retry_decisions d JOIN answer_attempts a ON a.user_id=d.user_id AND a.submission_id=d.submission_id WHERE a.room_question_id=? AND a.user_id=? AND d.use_chance=TRUE",Integer.class,question,user);}
    private boolean decided(UUID user,UUID submission){return jdbc.queryForObject("SELECT COUNT(*) FROM room_retry_decisions WHERE user_id=? AND submission_id=?",Integer.class,user,submission)>0;}
    private boolean pending(UUID user,List<Map<String,Object>> attempts){if(attempts.isEmpty())return false;var last=attempts.get(attempts.size()-1);return "RETRY".equals(last.get("outcome"))&&!decided(user,(UUID)last.get("submission_id"));}
    private Map<String,Object> pendingAttempt(QuizRoom r,Progress p){var attempts=data.attempts((UUID)question(r,p).get("id"),p.user);return pending(p.user,attempts)?attempts.get(attempts.size()-1):null;}
    private Instant decisionDeadline(Map<String,Object> attempt){return instant(attempt.get("accepted_at")).plus(CHANCE_WINDOW);}
    private void decline(QuizRoom r,Progress p,Map<String,Object> attempt){
        jdbc.update("INSERT INTO room_retry_decisions(user_id,submission_id,use_chance,decided_at) VALUES(?,?,FALSE,?)",p.user,attempt.get("submission_id"),at(clock.instant()));
        finalizeAnswer(r,p,((Number)attempt.get("selected_index")).intValue(),false,false);
    }
    public void start(QuizRoom r){
        r.startsAt=clock.instant().plusSeconds(5);r.status="QUESTION";r.deadline=r.startsAt;r.currentQuestion=0;
        UUID session=UUID.randomUUID();jdbc.update("INSERT INTO game_sessions(id,room_id,started_at) VALUES(?,?,?)",session,r.id,at(r.startsAt));
        jdbc.update("INSERT INTO scores(session_id,user_id,retries_remaining) SELECT ?,user_id,? FROM room_participants WHERE room_id=? AND role='PLAYER' AND left_at IS NULL",session,r.retryAllowance,r.id);
        jdbc.update("INSERT INTO room_player_progress(room_id,user_id,question_started_at,deadline) SELECT ?,user_id,?,? FROM room_participants WHERE room_id=? AND role='PLAYER' AND left_at IS NULL",r.id,at(r.startsAt),at(r.startsAt.plusSeconds(r.secondsPerQuestion)),r.id);
    }
    public RoomAttempt answer(QuizRoom r,UUID user,RoomAnswerRequest input){
        if(user.equals(r.hostId))throw denied("The host cannot answer or earn player points.");
        var old=data.replay(user,input.submissionId());
        if(!old.isEmpty()){
            var a=old.get(0);if(!r.id.equals(a.get("room_id"))||((Number)a.get("ordinal")).intValue()!=input.questionNumber()-1||((Number)a.get("selected_index")).intValue()!=input.selectedIndex())throw conflict("Submission ID already used.");
            return attempt(r,a);
        }
        Progress p=player(r,user);Instant acceptedAt=clock.instant();answerable(r,p,input.questionNumber(),acceptedAt,true);var q=question(r,p);UUID id=(UUID)q.get("id");
        if(data.finalized(id,user))throw conflict("Your response is final.");
        var attempts=data.attempts(id,user);if(pending(user,attempts))throw conflict("Choose whether to use a chance first.");
        var choices=bank.read((String)q.get("choices"));if(input.selectedIndex()<0||input.selectedIndex()>=choices.size())throw conflict("Unknown answer choice.");
        boolean correct=choices.get(input.selectedIndex()).equals(q.get("correct_answer"));int remaining=balance(r,user);
        String outcome=correct?"CORRECT":remaining>0?"RETRY":"INCORRECT";
        data.attempt(id,user,input.submissionId(),attempts.size()+1,input.selectedIndex(),correct,outcome,remaining,acceptedAt);
        if(outcome.equals("RETRY")){
            Instant remainingDeadline=p.deadline.isAfter(acceptedAt)?p.deadline:acceptedAt.plus(ANSWER_DELIVERY_GRACE);
            jdbc.update("UPDATE room_player_progress SET deadline=? WHERE room_id=? AND user_id=?",at(remainingDeadline.plus(CHANCE_WINDOW)),r.id,user);
        }
        if(!outcome.equals("RETRY"))finalizeAnswer(r,p,input.selectedIndex(),correct,false);
        schedule(r);return attempt(r,data.replay(user,input.submissionId()).get(0));
    }
    public void decide(QuizRoom r,UUID user,RoomRetryDecision input){
        if(user.equals(r.hostId))throw denied("The host cannot use player chances.");
        var records=data.replay(user,input.submissionId());if(records.isEmpty()||!r.id.equals(records.get(0).get("room_id")))throw conflict("That attempt is unavailable.");
        var previous=jdbc.queryForList("SELECT use_chance FROM room_retry_decisions WHERE user_id=? AND submission_id=?",user,input.submissionId());
        if(!previous.isEmpty()){if(!input.useChance().equals(previous.get(0).get("use_chance")))throw conflict("A decision was already saved.");return;}
        Progress p=player(r,user);UUID question=(UUID)question(r,p).get("id");var attempts=data.attempts(question,user);
        if(data.finalized(question,user)||!pending(user,attempts)||!input.submissionId().equals(attempts.get(attempts.size()-1).get("submission_id")))throw conflict("This response is already final.");
        var latest=attempts.get(attempts.size()-1);Instant expires=decisionDeadline(latest),decidedAt=clock.instant();
        if(!decidedAt.isBefore(p.deadline)){finalizeAnswer(r,p,((Number)latest.get("selected_index")).intValue(),false,true);schedule(r);return;}
        if(!decidedAt.isBefore(expires)){decline(r,p,latest);schedule(r);return;}
        answerable(r,p,p.ordinal+1,decidedAt,false);
        if(input.useChance()&&balance(r,user)<=0)throw conflict("No chances remain.");
        jdbc.update("INSERT INTO room_retry_decisions(user_id,submission_id,use_chance,decided_at) VALUES(?,?,?,?)",user,input.submissionId(),input.useChance(),at(clock.instant()));
        if(input.useChance()){
            if(!data.consumeChance(r.id,user))throw conflict("No chances remain.");
            Duration unused=Duration.between(decidedAt,expires);
            Duration paused=CHANCE_WINDOW.minus(unused);
            jdbc.update("UPDATE room_player_progress SET deadline=?,question_started_at=? WHERE room_id=? AND user_id=?",at(p.deadline.minus(unused)),at(p.started.plus(paused)),r.id,user);
        }
        else finalizeAnswer(r,p,((Number)records.get(0).get("selected_index")).intValue(),false,false);
        schedule(r);
    }
    private RoomAttempt attempt(QuizRoom r,Map<String,Object> a){
        UUID question=(UUID)a.get("room_question_id"),user=(UUID)a.get("user_id");String outcome=(String)a.get("outcome");
        int used=retries(question,user),maximum=((Number)a.get("question_maximum")).intValue();var submitted=data.submitted(question,user);
        Integer points=submitted.isEmpty()?null:((Number)submitted.get(0).get("points")).intValue();
        Instant expires=outcome.equals("RETRY")&&!decided(user,(UUID)a.get("submission_id"))?decisionDeadline(a):null;
        return new RoomAttempt((UUID)a.get("submission_id"),outcome,balance(r,user),instant(a.get("accepted_at")),points==null?scoring.correctPoints(r.scoringVersion,used,maximum):0,points,expires,expires==null?null:true);
    }
    private void answerable(QuizRoom r,Progress p,int number,Instant acceptedAt,boolean allowDeliveryGrace){
        Instant cutoff=allowDeliveryGrace?p.deadline.plus(ANSWER_DELIVERY_GRACE):p.deadline;
        if(!r.status.equals("QUESTION")||acceptedAt.isBefore(r.startsAt)||p.completed!=null||p.feedback!=null||p.ordinal+1!=number||!acceptedAt.isBefore(cutoff))throw conflict("This question is not accepting answers.");
    }
    private void finalizeAnswer(QuizRoom r,Progress p,Integer selected,boolean correct,boolean timeout){
        var q=question(r,p);UUID id=(UUID)q.get("id");if(data.finalized(id,p.user))return;
        var attempts=data.attempts(id,p.user);int used=retries(id,p.user);Instant now=timeout&&clock.instant().isAfter(p.deadline)?p.deadline:clock.instant();
        int points=correct?scoring.correctPoints(r.scoringVersion,used,((Number)q.get("points")).intValue()):0;
        data.answer(id,p.user,selected,correct,points,now,timeout,attempts.isEmpty()?null:Boolean.TRUE.equals(attempts.get(0).get("is_correct")),attempts.size(),used,Math.max(0,Duration.between(p.started,now).toMillis()));
        data.addScore(data.session(r.id),p.user,points,correct);
        jdbc.update("UPDATE room_player_progress SET feedback_until=? WHERE room_id=? AND user_id=?",at(clock.instant().plusMillis(1600)),r.id,p.user);
    }
    public boolean tick(QuizRoom r){
        if(!r.status.equals("QUESTION")||clock.instant().isBefore(r.startsAt))return false;
        boolean changed=r.deadline!=null&&r.deadline.equals(r.startsAt);
        for(Progress p:players(r)){
            if(p.completed!=null)continue;
            presented(r,p.ordinal,p.user,p.started);
            var chance=p.feedback==null?pendingAttempt(r,p):null;
            if(chance!=null){if(!clock.instant().isBefore(p.deadline)){finalizeAnswer(r,p,((Number)chance.get("selected_index")).intValue(),false,true);changed=true;}else if(!clock.instant().isBefore(decisionDeadline(chance))){decline(r,p,chance);changed=true;}continue;}
            if(p.feedback!=null&&!clock.instant().isBefore(p.feedback)){
                if(p.ordinal+1>=r.questionCount)jdbc.update("UPDATE room_player_progress SET completed_at=? WHERE room_id=? AND user_id=?",at(clock.instant()),r.id,p.user);
                else {jdbc.update("UPDATE room_player_progress SET ordinal=ordinal+1,question_started_at=?,deadline=?,feedback_until=NULL WHERE room_id=? AND user_id=?",at(clock.instant()),at(clock.instant().plusSeconds(r.secondsPerQuestion)),r.id,p.user);presented(r,p.ordinal+1,p.user,clock.instant());}
                changed=true;
            }else if(p.feedback==null&&!clock.instant().isBefore(p.deadline.plus(ANSWER_DELIVERY_GRACE))){var a=data.attempts((UUID)question(r,p).get("id"),p.user);Integer selected=a.isEmpty()?null:((Number)a.get(a.size()-1).get("selected_index")).intValue();finalizeAnswer(r,p,selected,false,true);changed=true;}
        }
        schedule(r);return changed;
    }
    private void presented(QuizRoom r,int ordinal,UUID user,Instant when){
        var q=data.current(r.id,ordinal);UUID id=(UUID)q.get("id");
        jdbc.update("UPDATE room_questions SET presented_at=? WHERE id=? AND presented_at IS NULL",at(when),id);
        jdbc.update("INSERT INTO room_question_presentations(room_question_id,user_id,presented_at) SELECT ?,?,? WHERE NOT EXISTS (SELECT 1 FROM room_question_presentations WHERE room_question_id=? AND user_id=?)",id,user,at(when),id,user);
    }
    private void schedule(QuizRoom r){
        if(clock.instant().isBefore(r.startsAt)){r.deadline=r.startsAt;return;}
        var active=players(r).stream().filter(p->p.completed==null).toList();
        if(active.isEmpty()){r.status="FINISHED";r.deadline=null;data.finish(r.id,clock.instant());}
        else r.deadline=active.stream().map(p->{if(p.feedback!=null)return p.feedback;var chance=pendingAttempt(r,p);if(chance==null)return p.deadline.plus(ANSWER_DELIVERY_GRACE);var expires=decisionDeadline(chance);return p.deadline.isBefore(expires)?p.deadline:expires;}).min(Instant::compareTo).orElse(null);
    }
    public void end(QuizRoom r){
        for(Progress p:players(r))if(p.completed==null){if(!clock.instant().isBefore(r.startsAt)&&p.feedback==null)finalizeAnswer(r,p,null,false,true);jdbc.update("UPDATE room_player_progress SET completed_at=? WHERE room_id=? AND user_id=?",at(clock.instant()),r.id,p.user);}
        r.status="FINISHED";r.deadline=null;data.finish(r.id,clock.instant());
    }
    public void leave(QuizRoom r,UUID user){
        Progress p=player(r,user);if(p.completed==null){data.forfeit(r.id,user);jdbc.update("UPDATE room_player_progress SET completed_at=? WHERE room_id=? AND user_id=?",at(clock.instant()),r.id,user);}schedule(r);
    }
    public RoomView view(QuizRoom r,UUID user,String publicUrl,Presence presence){
        boolean host=r.hostId.equals(user),finished=r.status.equals("FINISHED"),started=r.startsAt!=null&&!clock.instant().isBefore(r.startsAt);
        String status=r.status.equals("QUESTION")&&!started?"COUNTDOWN":r.status;
        var participants=data.participants(r.id).stream().map(row->{UUID id=(UUID)row.get("user_id");return new RoomParticipant(id,(String)row.get("display_name"),(String)row.get("role"),presence.isConnected(r.code,id),row.get("left_at")!=null,com.triviagame.online.auth.AvatarConfig.read((String)row.get("avatar_config"),id),(String)row.get("username"));}).toList();
        var allRanks=data.rankings(r.id);
        var rankings=allRanks.stream().filter(row->host||finished||user.equals(row.get("id"))).map(row->{UUID id=(UUID)row.get("id");return new RoomRanking(id,(String)row.get("display_name"),((Number)row.get("points")).intValue(),((Number)row.get("correct_count")).intValue(),host||finished?((Number)row.get("ranking")).intValue():0,com.triviagame.online.auth.AvatarConfig.read((String)row.get("avatar_config"),id),Boolean.TRUE.equals(row.get("forfeited")),(String)row.get("username"));}).toList();
        RoomQuestion question=null;Integer selected=null,awarded=null;RoomAttempt last=null;boolean finalized=false,completed=false;Instant deadline=null,feedback=null;int available=0;
        var progress=players(r);Progress p=progress.stream().filter(item->item.user.equals(user)).findFirst().orElse(null);
        if(p!=null){completed=p.completed!=null;deadline=p.deadline;feedback=p.feedback;
            if(started&&!completed&&!finished){var q=question(r,p);UUID id=(UUID)q.get("id");var answers=data.submitted(id,user);finalized=!answers.isEmpty();
                if(finalized){selected=(Integer)answers.get(0).get("selected_index");awarded=((Number)answers.get(0).get("points")).intValue();}
                var attempts=data.attempts(id,user);if(!attempts.isEmpty()){var a=attempts.get(attempts.size()-1);last=attempt(r,data.replay(user,(UUID)a.get("submission_id")).get(0));if(!finalized&&pending(user,attempts))selected=((Number)a.get("selected_index")).intValue();}
                available=finalized?0:scoring.correctPoints(r.scoringVersion,retries(id,user),((Number)q.get("points")).intValue());
                question=new RoomQuestion(p.ordinal+1,(String)q.get("text"),bank.read((String)q.get("choices")),(String)q.get("difficulty"),(String)q.get("question_type"),r.revealAnswers&&finalized?(String)q.get("correct_answer"):null);
            }
        }
        var answerCounts=host?jdbc.queryForList("SELECT a.user_id,COUNT(*) AS total FROM answers a JOIN room_questions q ON q.id=a.room_question_id WHERE q.room_id=? GROUP BY a.user_id",r.id):List.<Map<String,Object>>of();
        var hostProgress=host?progress.stream().map(item->new RoomView.PlayerProgress(item.user,Math.min(r.questionCount,item.ordinal+1),item.completed!=null,allRanks.stream().filter(row->row.get("id").equals(item.user)).map(row->((Number)row.get("points")).intValue()).findFirst().orElse(0),balance(r,item.user),answerCounts.stream().filter(row->row.get("user_id").equals(item.user)).map(row->((Number)row.get("total")).intValue()).findFirst().orElse(0),allRanks.stream().filter(row->row.get("id").equals(item.user)).map(row->((Number)row.get("correct_count")).intValue()).findFirst().orElse(0))).toList():List.<RoomView.PlayerProgress>of();
        int personal=rankings.stream().filter(row->row.id().equals(user)).mapToInt(RoomRanking::points).findFirst().orElse(0);
        return new RoomView(r.code,r.title,status,r.hostId,publicUrl+"/join/"+r.code,r.maxPlayers,r.questionCount,r.secondsPerQuestion,deadline,clock.instant(),participants,rankings,question,selected,r.retryAllowance,balance(r,user),finalized,last,available,awarded,r.scoringVersion,r.questionSource,r.openTdbCategoryId,r.difficulty,r.questionType,r.customQuizId,r.customQuizVersion,true,r.revealAnswers,r.startsAt,feedback,completed,personal,hostProgress);
    }
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static ResponseStatusException denied(String message){return new ResponseStatusException(HttpStatus.FORBIDDEN,message);}
}
