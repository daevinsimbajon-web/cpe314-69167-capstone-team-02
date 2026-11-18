package com.triviagame.online;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Real authenticated HTTP sessions, CSRF, migrations, background workers and SQL persistence. */
class StatisticsSharingTest extends RoomTestSupport {
    private static final String COMPARISONS="/analytics/comparisons";
    private void completedGame(Browser player)throws Exception {var guest=registered(username());String code=create(player);assertThat(join(guest,code).status()).isEqualTo(200);assertThat(player.post("/rooms/"+code+"/start",null).status()).isEqualTo(200);assertThat(player.post("/rooms/"+code+"/end",null).status()).isEqualTo(200);}
    private JsonNode awaitJob(Browser owner,Result submitted)throws Exception {
        assertThat(submitted.status()).isEqualTo(202);String id=submitted.body().get("id").asText();
        for(int i=0;i<500;i++) {JsonNode result=owner.get(COMPARISONS+"/import-jobs/"+id).body();
            assertThat(result.get("progress").asInt()).isBetween(0,100);
            if(Set.of("FAILED","COMPLETED").contains(result.get("status").asText()))return result;Thread.sleep(20);
        }throw new AssertionError("Import worker did not finish");
    }
    private JsonNode imported(Browser owner,JsonNode data)throws Exception {
        JsonNode job=awaitJob(owner,owner.post(COMPARISONS+"/import-jobs",data));assertThat(job.get("status").asText()).withFailMessage(job.toString()).isEqualTo("COMPLETED");return owner.get(COMPARISONS).body().get(0);
    }
    @Test void explicitConsentControlsEveryReadAndDisconnectRetainsOnlyImportedHistory()throws Exception {
        String viewerName=username();var viewer=registered(viewerName);var player=registered(username());var outsider=registered(username());
        completedGame(player);JsonNode exported=player.get("/analytics/export").body();
        var member=imported(viewer,exported);String memberId=member.get("membershipId").asText();
        assertThat(member.get("verified").asBoolean()).isTrue();assertThat(member.get("status").asText()).isEqualTo("SNAPSHOT");
        assertThat(viewer.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(204);
        assertThat(viewer.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(204);
        var pending=player.get("/analytics/sharing").body().get("pending");assertThat(pending.size()).isEqualTo(1);
        String request=pending.get(0).get("id").asText();
        assertThat(pending.toString()).contains("displayName","avatar").doesNotContain("email","password","requester_id");
        assertThat(viewer.post("/analytics/sharing/"+request+"/allow",null).status()).isEqualTo(404);
        assertThat(outsider.post("/analytics/sharing/"+request+"/allow",null).status()).isEqualTo(404);
        assertThat(outsider.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(404);
        completedGame(player);assertThat(viewer.get(COMPARISONS).body().get(0).get("snapshot").get("games").size()).isEqualTo(1);
        assertThat(player.post("/analytics/sharing/"+request+"/allow",null).status()).isEqualTo(204);
        var active=player.get("/analytics/sharing").body();assertThat(active.get("pending").size()).isZero();assertThat(active.get("active").size()).isEqualTo(1);
        var allowed=viewer.get(COMPARISONS);assertThat(allowed.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(allowed.body().get(0).get("status").asText()).isEqualTo("ALLOWED");assertThat(allowed.body().get(0).get("snapshot").get("games").size()).isEqualTo(2);
        assertThat(allowed.body().get(0).get("lastSyncedAt").isNull()).isFalse();
        var freshLogin=new Browser();assertThat(freshLogin.login(viewerName,PASSWORD).status()).isEqualTo(200);assertThat(freshLogin.get(COMPARISONS).body().size()).isEqualTo(1);
        completedGame(player);assertThat(viewer.get(COMPARISONS).body().get(0).get("snapshot").get("games").size()).isEqualTo(3);
        assertThat(player.post("/analytics/sharing/"+request+"/disconnect",null).status()).isEqualTo(204);
        assertThat(player.get("/analytics/sharing").body().get("active").size()).isZero();completedGame(player);
        var revoked=viewer.get(COMPARISONS).body().get(0);assertThat(revoked.get("status").asText()).isEqualTo("REVOKED");assertThat(revoked.get("snapshot").get("games").size()).isEqualTo(1);
        assertThat(revoked.get("canRequest").asBoolean()).isTrue();
        assertThat(viewer.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(204);
        var renewed=player.get("/analytics/sharing").body().get("pending");assertThat(renewed).hasSize(1);
        String newRequest=renewed.get(0).get("id").asText();assertThat(newRequest).isNotEqualTo(request);
        assertThat(player.post("/analytics/sharing/"+request+"/allow",null).status()).isEqualTo(404);
        assertThat(viewer.get(COMPARISONS).body().get(0).get("snapshot").get("games").size()).isEqualTo(1);
        assertThat(player.post("/analytics/sharing/"+newRequest+"/allow",null).status()).isEqualTo(204);
        assertThat(viewer.get(COMPARISONS).body().get(0).get("snapshot").get("games").size()).isEqualTo(4);
        assertThat(player.post("/analytics/sharing/"+newRequest+"/disconnect",null).status()).isEqualTo(204);
        assertThat(viewer.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(204);
        String deniedRenewal=player.get("/analytics/sharing").body().get("pending").get(0).get("id").asText();
        assertThat(player.post("/analytics/sharing/"+deniedRenewal+"/deny",null).status()).isEqualTo(204);
        var deniedMember=viewer.get(COMPARISONS).body().get(0);assertThat(deniedMember.get("status").asText()).isEqualTo("DENIED");assertThat(deniedMember.get("snapshot").get("games").size()).isEqualTo(1);
        assertThat(viewer.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(429);
        assertThat(player.get("/analytics/report").body().get("games").size()).isEqualTo(4);
        assertThat(viewer.delete(COMPARISONS+"/"+memberId).status()).isEqualTo(204);assertThat(viewer.get(COMPARISONS).body().size()).isZero();
        assertThat(new Browser().get(COMPARISONS).status()).isEqualTo(401);
    }
    @Test void denialCooldownAndMultipleConnectionsAreIndependent()throws Exception {
        var viewer=registered(username());var other=registered(username());var player=registered(username());
        var export=player.get("/analytics/export").body();var member=imported(viewer,export);var second=imported(other,export);
        String memberId=member.get("membershipId").asText();viewer.post(COMPARISONS+"/"+memberId+"/request",null);
        String denied=player.get("/analytics/sharing").body().get("pending").get(0).get("id").asText();
        assertThat(player.post("/analytics/sharing/"+denied+"/deny",null).status()).isEqualTo(204);
        assertThat(player.get("/analytics/sharing").body().get("pending").size()).isZero();assertThat(player.get("/analytics/sharing").body().get("active").size()).isZero();
        assertThat(viewer.get(COMPARISONS).body().get(0).get("status").asText()).isEqualTo("DENIED");
        assertThat(viewer.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(429);
        other.post(COMPARISONS+"/"+second.get("membershipId").asText()+"/request",null);
        String otherRequest=player.get("/analytics/sharing").body().get("pending").get(0).get("id").asText();player.post("/analytics/sharing/"+otherRequest+"/allow",null);
        clock.advance(Duration.ofDays(7));assertThat(viewer.post(COMPARISONS+"/"+memberId+"/request",null).status()).isEqualTo(204);
        assertThat(player.get("/analytics/sharing").body().get("pending").size()).isEqualTo(1);assertThat(player.get("/analytics/sharing").body().get("active").size()).isEqualTo(1);
        String renewed=player.get("/analytics/sharing").body().get("pending").get(0).get("id").asText();
        assertThat(player.post("/analytics/sharing/"+denied+"/allow",null).status()).isEqualTo(404);
        player.post("/analytics/sharing/"+renewed+"/allow",null);player.post("/analytics/sharing/"+renewed+"/disconnect",null);
        assertThat(player.get("/analytics/sharing").body().get("active").size()).isEqualTo(1);
    }
    @Test void unsignedAndTamperedIdentityNeverAuthorizeFutureStatistics()throws Exception {
        var owner=registered(username());var player=registered(username());completedGame(player);
        ObjectNode signed=(ObjectNode)player.get("/analytics/export").body(),unsigned=signed.deepCopy();unsigned.remove("identityProof");
        var snapshot=imported(owner,unsigned);assertThat(snapshot.get("verified").asBoolean()).isFalse();assertThat(snapshot.get("canRequest").asBoolean()).isFalse();
        assertThat(owner.post(COMPARISONS+"/"+snapshot.get("membershipId").asText()+"/request",null).status()).isEqualTo(403);
        ObjectNode forged=signed.deepCopy();((ObjectNode)forged.get("player")).put("displayName","Forged identity");
        assertThat(awaitJob(owner,owner.post(COMPARISONS+"/import-jobs",forged)).get("status").asText()).isEqualTo("FAILED");
        assertThat(owner.get(COMPARISONS).body().get(0).get("verified").asBoolean()).isFalse();
        var verified=imported(owner,signed);assertThat(verified.get("verified").asBoolean()).isTrue();
        assertThat(awaitJob(owner,owner.post(COMPARISONS+"/import-jobs",unsigned)).get("status").asText()).isEqualTo("FAILED");
        var own=imported(player,signed);assertThat(own.get("canRequest").asBoolean()).isFalse();assertThat(player.post(COMPARISONS+"/"+own.get("membershipId").asText()+"/request",null).status()).isEqualTo(403);
        assertThat(player.get(COMPARISONS+"/import-jobs/"+owner.get(COMPARISONS+"/import-jobs").body().get(0).get("id").asText()).status()).isEqualTo(404);
    }
    @Test void historicalImportsValidateDeduplicateAndRollBackConflicts()throws Exception {
        var owner=registered(username());var player=registered(username());completedGame(player);
        ObjectNode data=(ObjectNode)player.get("/analytics/export").body();data.remove("identityProof");imported(owner,data);
        ObjectNode unknown=data.deepCopy();((ObjectNode)unknown.get("player")).put("exportPlayerId","unknown-import-identity");
        assertThat(awaitJob(owner,owner.post(COMPARISONS+"/import-jobs?expected=*",unknown)).get("status").asText()).isEqualTo("FAILED");
        assertThat(awaitJob(owner,owner.post(COMPARISONS+"/import-jobs?expected=*",data)).get("status").asText()).isEqualTo("COMPLETED");
        var duplicate=awaitJob(owner,owner.post(COMPARISONS+"/import-jobs",data));assertThat(duplicate.get("message").asText()).contains("1 duplicate record skipped");
        ObjectNode conflict=data.deepCopy();((ObjectNode)conflict.get("games").get(0)).put("won",!conflict.get("games").get(0).get("won").asBoolean());
        assertThat(awaitJob(owner,owner.post(COMPARISONS+"/import-jobs",conflict)).get("message").asText()).contains("Existing records kept");
        ObjectNode invalid=data.deepCopy();((ObjectNode)invalid.get("games").get(0)).put("pointsEarned",999999);
        assertThat(awaitJob(owner,owner.post(COMPARISONS+"/import-jobs",invalid)).get("status").asText()).isEqualTo("FAILED");
        var malformed=owner.send("POST",COMPARISONS+"/import-jobs","{",true,true);
        assertThat(awaitJob(owner,malformed).get("status").asText()).isEqualTo("FAILED");
        assertThat(owner.get(COMPARISONS).body().get(0).get("snapshot").get("games").size()).isEqualTo(1);
        var response=owner.post(COMPARISONS+"/import-jobs?expected=another-player",data);assertThat(awaitJob(owner,response).get("message").asText()).contains("belongs to a different player");
    }
    @Test void largeImportRunsAsDurableBackgroundJobWithProgressAndResponsiveHttp()throws Exception {
        var owner=registered(username());var player=registered(username());completedGame(player);
        ObjectNode data=(ObjectNode)player.get("/analytics/export").body();data.remove("identityProof");JsonNode game=data.get("games").get(0).deepCopy();var games=data.putArray("games");
        for(int i=0;i<1500;i++){ObjectNode next=game.deepCopy();next.put("gameId","historical-"+i);games.add(next);}
        var submitted=owner.post(COMPARISONS+"/import-jobs",data);assertThat(submitted.status()).isEqualTo(202);
        assertThat(owner.get(COMPARISONS+"/import-jobs/"+submitted.body().get("id").asText()).status()).isEqualTo(200);
        assertThat(owner.get("/analytics/report").status()).isEqualTo(200);assertThat(awaitJob(owner,submitted).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(owner.get(COMPARISONS).body().get(0).get("snapshot").get("games").size()).isEqualTo(1500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistics_import_jobs WHERE id=? AND source_json IS NOT NULL",Integer.class,UUID.fromString(submitted.body().get("id").asText()))).isZero();
    }
}
