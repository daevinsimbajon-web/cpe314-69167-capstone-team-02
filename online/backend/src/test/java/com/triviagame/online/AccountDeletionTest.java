package com.triviagame.online;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountDeletionTest extends RoomTestSupport {
    private Map<String,String> confirmation(String password) {return Map.of("password",password,"confirmation","DELETE");}
    private Result remove(Browser browser,String password) throws Exception {
        return browser.send("DELETE","/account/me",confirmation(password),false,true);
    }

    @Test void verifiesPasswordConfirmationCsrfAndAuthenticationBeforeChangingAnything() throws Exception {
        Browser owner=registered(username());String code=create(owner);
        assertThat(remove(owner,"WrongPassword42!").status()).isEqualTo(403);
        assertThat(owner.get("/me").status()).isEqualTo(200);
        assertThat(owner.get("/rooms/"+code).body().get("status").asText()).isEqualTo("LOBBY");
        assertThat(owner.send("DELETE","/account/me",Map.of("password",PASSWORD,"confirmation","delete"),false,true).status()).isEqualTo(400);
        assertThat(owner.send("DELETE","/account/me",confirmation(PASSWORD),false,false).status()).isEqualTo(403);
        assertThat(remove(new Browser(),PASSWORD).status()).isEqualTo(401);
        assertThat(owner.get("/me").status()).isEqualTo(200);
    }

    @Test void erasesPersonalFieldsAndCredentialsAndInvalidatesEveryDevice() throws Exception {
        String name=username();Browser first=registered(name),second=new Browser();
        assertThat(second.login(name+"@example.com",PASSWORD).status()).isEqualTo(200);
        UUID owner=UUID.fromString(first.get("/me").body().get("id").asText());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name=?",Integer.class,owner.toString())).isEqualTo(2);
        assertThat(remove(first,PASSWORD).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name=?",Integer.class,owner.toString())).isZero();
        assertThat(first.get("/me").status()).isEqualTo(401);
        assertThat(second.get("/me").status()).isEqualTo(401);
        assertThat(new Browser().login(name,PASSWORD).status()).isEqualTo(401);
        assertThat(new Browser().login(name+"@example.com",PASSWORD).status()).isEqualTo(401);
        var saved=jdbc.queryForMap("SELECT display_name,username,email,deleted_at FROM users WHERE id=?",owner);
        assertThat(saved.get("display_name")).isEqualTo("Deleted player");
        assertThat(saved.get("username")).isNotEqualTo(name);
        assertThat(saved.get("email")).isNotEqualTo(name+"@example.com");
        assertThat(saved.get("deleted_at")).isNotNull();
    }

    @Test void cannotTargetAnotherAccountByPathOrRequestBody() throws Exception {
        Browser first=registered(username()),second=registered(username());
        String other=second.get("/me").body().get("id").asText();
        assertThat(first.send("DELETE","/account/"+other,confirmation(PASSWORD),false,true).status()).isEqualTo(404);
        assertThat(first.send("DELETE","/account/me",Map.of("password",PASSWORD,"confirmation","DELETE","userId",other),false,true).status()).isEqualTo(400);
        assertThat(first.get("/me").status()).isEqualTo(200);
        assertThat(second.get("/me").status()).isEqualTo(200);
    }

    @Test void closesHostedLobbiesWithoutDeletingOtherParticipants() throws Exception {
        Browser host=registered(username()),player=registered(username()),newPlayer=registered(username());
        String code=create(host);assertThat(join(player,code).status()).isEqualTo(200);
        assertThat(remove(host,PASSWORD).status()).isEqualTo(204);
        var room=player.get("/rooms/"+code);
        assertThat(room.status()).isEqualTo(200);
        assertThat(room.body().get("status").asText()).isEqualTo("CLOSED");
        assertThat(join(newPlayer,code).status()).isEqualTo(409);
        assertThat(player.get("/me").status()).isEqualTo(200);
    }

    @Test void endsHostedQuizzesAndPreservesOtherPlayersScoresAndHistory() throws Exception {
        Browser host=registered(username()),player=registered(username());
        String code=create(host);join(player,code);host.post("/rooms/"+code+"/start",null);
        var question=player.get("/rooms/"+code).body().get("question");
        int expected=10;
        player.post("/rooms/"+code+"/answers",Map.of("questionNumber",1,"selectedIndex",correctIndex(code,question)));
        assertThat(remove(host,PASSWORD).status()).isEqualTo(204);
        var room=player.get("/rooms/"+code).body();
        assertThat(room.get("status").asText()).isEqualTo("FINISHED");
        assertThat(room.get("title").asText()).isEqualTo("Quiz room");
        assertThat(room.get("leaderboard").toString()).contains("Deleted player");
        var history=player.get("/history").body();assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("points").asInt()).isEqualTo(expected);
        assertThat(player.get("/me").body().get("statistics").get("averageAccuracy").asDouble()).isEqualTo(100.0);
    }

    @Test void deletingAPlayerDoesNotEndAnotherHostsQuiz() throws Exception {
        Browser host=registered(username()),player=registered(username());
        String code=create(host);join(player,code);host.post("/rooms/"+code+"/start",null);
        assertThat(remove(player,PASSWORD).status()).isEqualTo(204);
        var room=host.get("/rooms/"+code).body();
        assertThat(room.get("status").asText()).isEqualTo("QUESTION");
        assertThat(room.get("participants").toString()).contains("Deleted player");
        assertThat(host.post("/rooms/"+code+"/end",null).status()).isEqualTo(200);
        assertThat(host.get("/history").body().size()).isEqualTo(1);
    }
}
