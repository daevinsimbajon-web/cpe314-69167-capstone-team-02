package com.triviagame.online;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ProfileEditingTest extends HttpTestSupport {
    @Test void namesAreValidatedAndUsernameEditsPreserveIdentityAndAvatar()throws Exception{
        String first=username(),second=username();var a=registered(first);registered(second);var before=a.get("/me").body();String next=username();
        assertThat(a.patch("/me",Map.of("displayName","Same Name","username",second.toUpperCase(Locale.ROOT))).status()).isEqualTo(409);
        assertThat(a.patch("/me",Map.of("displayName"," ","username",next)).status()).isEqualTo(400);
        assertThat(a.patch("/me",Map.of("displayName","x".repeat(41),"username",next)).status()).isEqualTo(400);
        var result=a.patch("/me",Map.of("displayName","J","username",next.toUpperCase(Locale.ROOT)));assertThat(result.status()).as(result.body().toString()).isEqualTo(200);assertThat(result.body().get("id")).isEqualTo(before.get("id"));assertThat(result.body().get("avatar")).isEqualTo(before.get("avatar"));assertThat(result.body().get("username").asText()).isEqualTo(next);assertThat(a.get("/me").body().get("displayName").asText()).isEqualTo("J");assertThat(a.login(next,PASSWORD).status()).isEqualTo(200);
        assertThat(a.get("/auth/username-availability?username="+next).body().get("available").asBoolean()).isTrue();assertThat(new Browser().get("/auth/username-availability?username="+next).body().get("available").asBoolean()).isFalse();
    }
    @Test void concurrentUsernameEditsHaveOneWinnerAndKeepBothAccountIdentities()throws Exception{
        var one=registered(username());var two=registered(username());String candidate=username();
        String firstId=one.get("/me").body().get("id").asText(),secondId=two.get("/me").body().get("id").asText();
        java.util.function.Function<Browser,Integer> edit=browser->{try{return browser.patch("/me",Map.of("displayName","Same Name","username",candidate)).status();}catch(Exception failure){throw new RuntimeException(failure);}};
        var first=java.util.concurrent.CompletableFuture.supplyAsync(()->edit.apply(one));var second=java.util.concurrent.CompletableFuture.supplyAsync(()->edit.apply(two));
        assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,409);assertThat(one.get("/me").body().get("id").asText()).isEqualTo(firstId);assertThat(two.get("/me").body().get("id").asText()).isEqualTo(secondId);
    }
}
