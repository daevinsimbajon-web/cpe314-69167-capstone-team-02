package com.triviagame.online;

import com.triviagame.online.auth.AvatarConfig;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AvatarTest extends RoomTestSupport {
    @Test void everySpeciesPersistsWithoutChangingThePaletteAndInvalidSpeciesAreRejected() throws Exception {
        var owner=registered(username());
        for(int species=0;species<8;species++){var payload=owl();payload.put("species",species);assertThat(owner.patch("/me/avatar",payload).status()).isEqualTo(200);assertThat(owner.get("/me").body().get("avatar")).isEqualTo(json.valueToTree(payload));}
        for(var invalid:List.of(-1,8,1.5,"raven")){var payload=owl();payload.put("species",invalid);assertThat(owner.patch("/me/avatar",payload).status()).isEqualTo(400);}
    }
    Map<String,Object> owl(){return new LinkedHashMap<>(Map.of("version",1,"body",3,"eyes",2,"iris",1,"wings",2,"marking",1,"accessory",4,"expression",0));}
    @Test void registrationPersistsAnOwlAndAnotherBrowserRehydratesIt() throws Exception {
        String name=username();Browser first=new Browser();var registered=first.post("/auth/register",registration(name,name+"@example.com"));
        assertThat(registered.status()).isEqualTo(201);verifyRegistration(first,registered,name+"@example.com");var me=first.get("/me");var avatar=me.body().get("avatar");assertThat(avatar.get("version").asInt()).isEqualTo(1);
        UUID id=UUID.fromString(me.body().get("id").asText());assertThat(jdbc.queryForObject("SELECT avatar_config FROM users WHERE id=?",String.class,id)).isNotBlank();
        first.login(name,PASSWORD);assertThat(first.get("/me").body().get("avatar")).isEqualTo(avatar);
        Browser otherDevice=new Browser();assertThat(otherDevice.login(name,PASSWORD).status()).isEqualTo(200);assertThat(otherDevice.get("/me").body().get("avatar")).isEqualTo(avatar);
    }
    @Test void savingOnlyChangesTheAuthenticatedOwnerAndRejectsUnboundedMarkupOrMissingFields() throws Exception {
        var a=registered(username());var b=registered(username());var original=b.get("/me").body().get("avatar");
        assertThat(a.patch("/me/avatar",owl()).status()).isEqualTo(200);assertThat(a.get("/me").body().get("avatar")).isEqualTo(json.valueToTree(owl()));assertThat(b.get("/me").body().get("avatar")).isEqualTo(original);
        for(var invalid:List.of(Map.of("body",9),Map.of("version",2),Map.of("eyes",4),Map.of("body",-1),Map.of("svg","<script/>"),Map.of("userId",b.get("/me").body().get("id").asText()))){var payload=owl();payload.putAll(invalid);assertThat(a.patch("/me/avatar",payload).status()).isEqualTo(400);}
        var missing=owl();missing.remove("accessory");assertThat(a.patch("/me/avatar",missing).status()).isEqualTo(400);
        assertThat(new Browser().patch("/me/avatar",owl()).status()).isEqualTo(401);
        assertThat(a.get("/me").body().get("avatar")).isEqualTo(json.valueToTree(owl()));
    }
    @Test void legacyFallbackAndRoomExportViewsKeepTheSameIdentity() throws Exception {
        String name=username();var player=registered(name);UUID id=UUID.fromString(player.get("/me").body().get("id").asText());
        jdbc.update("UPDATE users SET avatar_config=NULL WHERE id=?",id);
        var fallback=player.get("/me").body().get("avatar");assertThat(fallback).isEqualTo(json.valueToTree(AvatarConfig.forUser(id)));assertThat(player.get("/me").body().get("avatar")).isEqualTo(fallback);
        var host=registered(username());String code=create(host);join(player,code);
        var roster=player.get("/rooms/"+code).body().get("participants");for(var row:roster)if(row.get("id").asText().equals(id.toString()))assertThat(row.get("avatar")).isEqualTo(fallback);
        player.patch("/me/avatar",owl());host.post("/rooms/"+code+"/start",null);host.post("/rooms/"+code+"/end",null);
        var result=player.get("/rooms/"+code).body().get("leaderboard");for(var row:result)if(row.get("id").asText().equals(id.toString()))assertThat(row.get("avatar")).isEqualTo(json.valueToTree(owl()));
        assertThat(player.get("/analytics/export").body().get("player").get("avatar")).isEqualTo(json.valueToTree(owl()));
    }
}
