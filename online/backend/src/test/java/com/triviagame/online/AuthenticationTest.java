package com.triviagame.online;

import com.triviagame.online.auth.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class AuthenticationTest extends HttpTestSupport {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Test void registrationHashesPasswordAndNeverReturnsIt() throws Exception {
        String name=username(); Browser browser=new Browser();
        Result result=browser.post("/auth/register",registration(name,name+"@example.com"));
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().get("purpose").asText()).isEqualTo("EMAIL_VERIFICATION");
        assertThat(result.body().has("ticket")).isTrue();
        assertThat(result.body().has("password")).isFalse(); assertThat(result.body().has("passwordHash")).isFalse();
        UserAccount saved=users.findByUsernameOrEmail(name,name).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD,saved.getPasswordHash())).isTrue();
    }
    @Test void duplicateEmailAndUsernameAreCaseInsensitive() throws Exception {
        String name=username(); Browser browser=new Browser();
        assertThat(browser.post("/auth/register",registration(name,name+"@example.com")).status()).isEqualTo(201);
        assertThat(browser.post("/auth/register",registration(username(),name.toUpperCase(Locale.ROOT)+"@EXAMPLE.COM")).status()).isEqualTo(409);
        assertThat(browser.post("/auth/register",registration(name.toUpperCase(Locale.ROOT),username()+"@example.com")).status()).isEqualTo(409);
    }
    @Test void loginByEmailAndUsernamePersistsSessionAndLogoutRevokesIt() throws Exception {
        String name=username(); Browser initial=new Browser();
        Result pending=initial.post("/auth/register",registration(name,name+"@example.com"));
        assertThat(pending.status()).isEqualTo(201);verifyRegistration(initial,pending,name+"@example.com");
        for(String identifier:List.of(name,name+"@example.com")) {
            Browser browser=new Browser(); browser.get("/auth/csrf");
            String before=browser.cookies.getCookieStore().getCookies().get(0).getValue();
            Result login=browser.login(identifier,PASSWORD); assertThat(login.status()).isEqualTo(200);
            assertThat(browser.cookies.getCookieStore().getCookies().get(0).getValue()).isNotEqualTo(before);
            assertThat(login.headers().allValues("set-cookie").toString()).contains("HttpOnly","SameSite=Lax");
            Result me=browser.get("/me"); assertThat(me.status()).isEqualTo(200);
            assertThat(me.body().get("username").asText()).isEqualTo(name);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name=?",Integer.class,
                    me.body().get("id").asText())).isPositive();
            assertThat(browser.post("/auth/logout",null).status()).isEqualTo(204);
            assertThat(browser.get("/me").status()).isEqualTo(401);
        }
    }
    @Test void rejectsMissingCsrfAndInvalidCredentialsAndUnapprovedOrigins() throws Exception {
        Browser browser=new Browser();
        assertThat(browser.send("POST","/auth/register",Map.of(),false,false).status()).isEqualTo(403);
        assertThat(browser.login("unknown",PASSWORD).status()).isEqualTo(401);
        assertThat(browser.get("/rooms").status()).isEqualTo(401);
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/me"))
                .header("Origin","https://unapproved.example").GET().build();
        assertThat(browser.client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
    }
    @Test void rejectsWeakPasswordsAndMismatchedConfirmation() throws Exception {
        Browser browser=new Browser(); String name=username();
        var data=new HashMap<>(registration(name,name+"@example.com"));
        data.put("password","short"); data.put("confirmPassword","different");
        assertThat(browser.post("/auth/register",data).status()).isEqualTo(400);
        assertThat(users.findByUsernameOrEmail(name,name)).isEmpty();
    }
    @Test void acceptsEightCharactersAndRejectsSevenWithoutBypassingVerification() throws Exception {
        String name=username();Browser browser=new Browser();var data=new HashMap<>(registration(name,name+"@example.com"));
        data.put("password","Short42");data.put("confirmPassword","Short42");
        assertThat(browser.post("/auth/register",data).status()).isEqualTo(400);
        assertThat(users.findByUsernameOrEmail(name,name)).isEmpty();
        data.put("password","Night42!");data.put("confirmPassword","Night42!");
        Result pending=browser.post("/auth/register",data);assertThat(pending.status()).isEqualTo(201);
        assertThat(browser.login(name,"Night42!").status()).isEqualTo(401);
        verifyRegistration(browser,pending,name+"@example.com");
        assertThat(browser.login(name,"Night42!").status()).isEqualTo(200);
        assertThat(passwords.matches("Night42!",users.findByUsernameOrEmail(name,name).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void profileEditsPersistAndPrivateEndpointsRequireAuthentication() throws Exception {
        Browser browser=registered(username());
        assertThat(browser.patch("/me",Map.of("displayName","New name")).status()).isEqualTo(200);
        assertThat(browser.get("/me").body().get("displayName").asText()).isEqualTo("New name");
        assertThat(new Browser().get("/me").status()).isEqualTo(401);
    }
}
