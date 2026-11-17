package com.triviagame.online.analytics;

import com.fasterxml.jackson.databind.*;
import com.triviagame.online.auth.AvatarConfig;
import java.time.*;
import java.util.*;
import java.util.function.IntConsumer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Comparisons {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final Clock clock;
    private final SnapshotValidation validation;private final ExportSigning signing;private final AnalyticsService analytics;
    public Comparisons(JdbcTemplate jdbc,ObjectMapper json,Clock clock,SnapshotValidation validation,ExportSigning signing,AnalyticsService analytics){
        this.jdbc=jdbc;this.json=json;this.clock=clock;this.validation=validation;this.signing=signing;this.analytics=analytics;
    }
    public record Member(UUID membershipId,AnalyticsExport snapshot,String label,boolean verified,String status,boolean canRequest,Instant lastSyncedAt) { }
    public record Connection(UUID id,String displayName,AvatarConfig avatar,String status) { }
    public record Inbox(List<Connection> pending,List<Connection> active) { }
    private Instant instant(Object value){return value==null?null:com.triviagame.online.DatabaseTimes.instant(value);}
    private OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
    private void lockOwner(UUID owner){jdbc.queryForObject("SELECT id FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE",UUID.class,owner);}
    private Map<String,Object> member(UUID owner,UUID id){
        return jdbc.queryForList("SELECT * FROM compared_players WHERE owner_id=? AND id=?",owner,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private List<Map<String,Object>> permission(UUID owner,UUID target){
        return jdbc.queryForList("SELECT * FROM statistics_sharing WHERE requester_id=? AND recipient_id=? FOR UPDATE",owner,target);
    }
    private boolean canRequest(Map<String,Object> permission){
        return List.of("DENIED","REVOKED").contains(permission.get("status"))&&instant(permission.get("updated_at")).plus(Duration.ofDays(7)).compareTo(clock.instant())<=0;
    }
    @Transactional
    public List<Member> list(UUID owner) {
        lockOwner(owner);var result=new ArrayList<Member>();
        for(var row:jdbc.queryForList("SELECT * FROM compared_players WHERE owner_id=? ORDER BY exported_at,id",owner)) {
            UUID id=(UUID)row.get("id"),target=(UUID)row.get("verified_user_id");
            boolean verified=target!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND deleted_at IS NULL",Integer.class,target)==1;
            var permissions=verified?permission(owner,target):List.<Map<String,Object>>of();
            String status=permissions.isEmpty()?"SNAPSHOT":(String)permissions.get(0).get("status");
            var games=new LinkedHashMap<String,AnalyticsExport.Game>();
            for(String stored:jdbc.queryForList("SELECT game_json FROM compared_games WHERE member_id=? ORDER BY game_id",String.class,id)) {
                var game=read(stored,AnalyticsExport.Game.class);games.put(game.gameId(),game);
            }
            AnalyticsExport.Player player=read((String)row.get("player_json"),AnalyticsExport.Player.class);
            Instant synced=instant(row.get("last_synced_at"));
            if(status.equals("ALLOWED")) {
                // The permission row stays locked until this read finishes, serializing it with revocation.
                var latest=analytics.export(target,null,null,"UTC");player=latest.player();
                latest.games().forEach(g->games.put(g.gameId(),g));synced=clock.instant();
                jdbc.update("UPDATE compared_players SET last_synced_at=? WHERE id=?",now(),id);
            }
            result.add(new Member(id,new AnalyticsExport("hootly.analytics",1,instant(row.get("exported_at")),player,new ArrayList<>(games.values())),
                (String)row.get("label"),verified,status,verified&&!owner.equals(target)&&(permissions.isEmpty()||canRequest(permissions.get(0))),synced));
        }return result;
    }
    @Transactional
    public String importHistory(UUID owner,String source,String expected,IntConsumer progress) {
        JsonNode raw;try{raw=json.reader().with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(source);}catch(Exception invalid){throw new IllegalArgumentException("This file is not valid JSON.");}
        if(raw==null)throw new IllegalArgumentException("This file is empty.");
        var data=validation.read(raw);UUID verified=signing.verify(raw);progress.accept(20);lockOwner(owner);
        String exportId=data.player().exportPlayerId();
        if(expected!=null&&!expected.equals("*")&&!expected.equals(exportId))throw new IllegalArgumentException("This export belongs to a different player. Use Import Player Stats to add someone new.");
        var rows=jdbc.queryForList("SELECT * FROM compared_players WHERE owner_id=? AND export_player_id=?",owner,exportId);
        if(expected!=null&&rows.isEmpty())throw new IllegalArgumentException("This player was removed. Import the player again.");
        boolean created=rows.isEmpty();UUID id=created?UUID.randomUUID():(UUID)rows.get(0).get("id");
        if(created&&jdbc.queryForObject("SELECT COUNT(*) FROM compared_players WHERE owner_id=?",Integer.class,owner)>=20)throw new IllegalArgumentException("Compare at most 20 players at once.");
        if(!created&&rows.get(0).get("verified_user_id")!=null&&verified==null)throw new IllegalArgumentException("Use a signed Hootly export to update this verified history.");
        var incoming=new LinkedHashMap<String,String>();int duplicates=0,added=0;
        for(var game:data.games()) {String encoded=validation.gameJson(game),prior=incoming.putIfAbsent(game.gameId(),encoded);
            if(prior!=null){if(!prior.equals(encoded))throw conflict();duplicates++;}}
        var saved=new HashMap<String,String>();
        if(!created)jdbc.query("SELECT game_id,game_json FROM compared_games WHERE member_id=?",rs->{saved.put(rs.getString(1),rs.getString(2));},id);
        for(var entry:incoming.entrySet()){String old=saved.get(entry.getKey());if(old==null)added++;else if(old.equals(entry.getValue()))duplicates++;else throw conflict();}
        if(saved.size()+added>5000||jdbc.queryForObject("SELECT COUNT(*) FROM compared_games g JOIN compared_players p ON p.id=g.member_id WHERE p.owner_id=?",Integer.class,owner)+added>10000)
            throw new IllegalArgumentException("Keep at most 5,000 games per player and 10,000 per comparison.");
        if(created)jdbc.update("INSERT INTO compared_players(id,owner_id,export_player_id,verified_user_id,label,player_json,exported_at) VALUES(?,?,?,?,?,?,?)",
            id,owner,exportId,verified,data.player().displayName(),validation.write(data.player()),data.exportedAt().atOffset(ZoneOffset.UTC));
        else {
            // Older files may add records but never replace more recent player metadata.
            if(!data.exportedAt().isBefore(instant(rows.get(0).get("exported_at"))))jdbc.update("UPDATE compared_players SET player_json=?,exported_at=? WHERE id=?",validation.write(data.player()),data.exportedAt().atOffset(ZoneOffset.UTC),id);
            if(verified!=null)jdbc.update("UPDATE compared_players SET verified_user_id=? WHERE id=?",verified,id);
        }
        int count=0;
        for(var entry:incoming.entrySet()) {
            if(!saved.containsKey(entry.getKey()))jdbc.update("INSERT INTO compared_games(member_id,game_id,game_json) VALUES(?,?,?)",id,entry.getKey(),entry.getValue());
            if(++count%100==0)progress.accept(20+70*count/Math.max(1,incoming.size()));
        }
        return (created?"1 player imported. ":"")+(added==0?"Already up to date · No new records found.":added+" new "+(added==1?"record":"records")+" added.")+" "+duplicates+" duplicate "+(duplicates==1?"record":"records")+" skipped.";
    }
    private IllegalArgumentException conflict(){return new IllegalArgumentException("Conflicting records found. Existing records kept; this file was not merged. Ask the player for a consistent export.");}
    @Transactional
    public void request(UUID owner,UUID id) {
        lockOwner(owner);var row=member(owner,id);UUID target=(UUID)row.get("verified_user_id");
        if(target==null||target.equals(owner)||jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND deleted_at IS NULL",Integer.class,target)!=1)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Automatic updates are unavailable for this imported history.");
        var permissions=permission(owner,target);
        if(!permissions.isEmpty()) {
            var p=permissions.get(0);if(List.of("PENDING","ALLOWED").contains(p.get("status")))return;
            if(!canRequest(p))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait seven days after a declined or disconnected request.");
            jdbc.update("UPDATE statistics_sharing SET status='PENDING',requested_at=?,updated_at=? WHERE id=?",now(),now(),p.get("id"));
        }else jdbc.update("INSERT INTO statistics_sharing(id,requester_id,recipient_id,status,requested_at,updated_at) VALUES(?,?,?,'PENDING',?,?)",UUID.randomUUID(),owner,target,now(),now());
    }
    @Transactional(readOnly=true)
    public Inbox inbox(UUID user) {
        var rows=jdbc.query("""
            SELECT s.id,s.status,u.id AS user_id,u.display_name,u.avatar_config FROM statistics_sharing s
            JOIN users u ON u.id=s.requester_id WHERE s.recipient_id=? AND u.deleted_at IS NULL
            AND s.status IN ('PENDING','ALLOWED') ORDER BY s.requested_at,s.id
            """,(rs,n)->new Connection(rs.getObject("id",UUID.class),rs.getString("display_name"),AvatarConfig.read(rs.getString("avatar_config"),rs.getObject("user_id",UUID.class)),rs.getString("status")),user);
        return new Inbox(rows.stream().filter(c->c.status().equals("PENDING")).toList(),rows.stream().filter(c->c.status().equals("ALLOWED")).toList());
    }
    @Transactional
    public void decide(UUID recipient,UUID request,String action) {
        var p=jdbc.queryForList("SELECT * FROM statistics_sharing WHERE id=? AND recipient_id=? FOR UPDATE",request,recipient).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String status=(String)p.get("status");
        if(action.equals("disconnect")) {
            if(status.equals("REVOKED"))return;
            if(!status.equals("ALLOWED"))throw new ResponseStatusException(HttpStatus.CONFLICT,"This connection is not active.");
            jdbc.update("UPDATE statistics_sharing SET status='REVOKED',revoked_at=?,updated_at=? WHERE id=?",now(),now(),request);
        }else {
            String next=action.equals("allow")?"ALLOWED":"DENIED";
            if(status.equals(next))return;
            if(!status.equals("PENDING"))throw new ResponseStatusException(HttpStatus.CONFLICT,"This request was already handled.");
            jdbc.update("UPDATE statistics_sharing SET status=?,granted_at=?,revoked_at=NULL,updated_at=? WHERE id=?",next,next.equals("ALLOWED")?now():null,now(),request);
        }
    }
    @Transactional
    public void remove(UUID owner,UUID id) {
        lockOwner(owner);var row=member(owner,id);
        jdbc.update("UPDATE statistics_sharing SET status='REVOKED',revoked_at=?,updated_at=? WHERE requester_id=? AND recipient_id=? AND status IN ('PENDING','ALLOWED')",now(),now(),owner,row.get("verified_user_id"));
        jdbc.update("DELETE FROM compared_players WHERE id=? AND owner_id=?",id,owner);
    }
    @Transactional
    public void rename(UUID owner,UUID id,String label){lockOwner(owner);member(owner,id);jdbc.update("UPDATE compared_players SET label=? WHERE id=? AND owner_id=?",label,id,owner);}
    private <T>T read(String source,Class<T> type){try{return json.readValue(source,type);}catch(Exception invalid){throw new IllegalStateException("Invalid stored comparison",invalid);}}
}
