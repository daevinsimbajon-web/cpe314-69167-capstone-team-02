package com.triviagame.online;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RoomRemovalTest extends RoomTestSupport {
    @Test void hostRemovalHasAnExplicitCodeAndPersistsAcrossReads()throws Exception{
        var host=registered(username());var player=registered(username());var outsider=registered(username());String code=create(host),path="/rooms/"+code;
        assertThat(join(player,code).status()).isEqualTo(200);String playerId=player.get("/me").body().get("id").asText();
        assertThat(host.delete(path+"/participants/"+playerId).status()).isEqualTo(204);
        for(int i=0;i<2;i++){var removed=player.get(path);assertThat(removed.status()).isEqualTo(403);assertThat(removed.body().get("code").asText()).isEqualTo("ROOM_REMOVED");assertThat(removed.body().get("message").asText()).isEqualTo("You’ve been removed from the room.");}
        var denied=outsider.get(path);assertThat(denied.status()).isEqualTo(403);assertThat(denied.body().has("code")).isFalse();
        assertThat(host.get(path).status()).isEqualTo(200);
    }
    @Test void voluntaryDepartureIsNotAKickAndRejoiningClearsTheRemovalFlag()throws Exception{
        var host=registered(username());var player=registered(username());String code=create(host),path="/rooms/"+code;
        join(player,code);assertThat(player.post(path+"/leave",Map.of()).status()).isEqualTo(204);assertThat(player.get(path).body().has("code")).isFalse();
        join(player,code);String playerId=player.get("/me").body().get("id").asText();host.delete(path+"/participants/"+playerId);
        assertThat(player.get(path).body().get("code").asText()).isEqualTo("ROOM_REMOVED");assertThat(join(player,code).status()).isEqualTo(200);assertThat(player.get(path).status()).isEqualTo(200);
    }
}
