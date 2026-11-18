package com.triviagame.online;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import com.triviagame.online.mail.EmailKind;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** Replays trusted HTTPS proxy metadata without disabling the real SecurityFilterChain. */
@TestPropertySource(properties={
        "app.allowed-origins=http://localhost:5173,https://approved-tunnel.example",
        "app.public-url=https://approved-tunnel.example"
})
class ForwardedAuthenticationTest extends HttpTestSupport {
    private final HttpClient proxy=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpRequest.Builder forwarded(String path,String origin){
        return HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path))
                .header("X-Forwarded-Proto","https")
                .header("X-Forwarded-Host","approved-tunnel.example")
                .header("X-Forwarded-Port","443")
                .header("X-Forwarded-For","203.0.113.42")
                .header("Origin",origin);
    }

    @Test void publicHttpsLoginUsesSecureHostOnlyCookiesAndKeepsCsrfAndOriginChecks() throws Exception {
        String name=username();registered(name);
        var csrf=proxy.send(forwarded("/auth/csrf","https://approved-tunnel.example").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(200);
        String setCookie=csrf.headers().firstValue("set-cookie").orElseThrow();
        assertThat(setCookie).contains("Secure","HttpOnly","SameSite=Lax").doesNotContain("Domain=");
        String cookie=setCookie.split(";",2)[0];var token=json.readTree(csrf.body());
        var login=proxy.send(forwarded("/auth/login","https://approved-tunnel.example")
                .header("Cookie",cookie).header(token.get("headerName").asText(),token.get("token").asText())
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username="+name+"&password="+PASSWORD)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        String authenticated=login.headers().firstValue("set-cookie").orElseThrow().split(";",2)[0];
        assertThat(authenticated).isNotEqualTo(cookie);
        var me=proxy.send(forwarded("/me","https://approved-tunnel.example")
                .header("Cookie",authenticated).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).path("username").asText()).isEqualTo(name);
        assertThat(proxy.send(forwarded("/auth/login","https://approved-tunnel.example")
                .header("Cookie",authenticated).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        assertThat(proxy.send(forwarded("/me","https://unapproved.example")
                .header("Cookie",authenticated).GET().build(),HttpResponse.BodyHandlers.discarding())
                .statusCode()).isEqualTo(403);
        assertThat(new Browser().get("/auth/csrf").headers().allValues("set-cookie").toString())
                .doesNotContain("; Secure");
    }

    @Test void trustedHttpsPasswordRecoverySetsAndClearsSecurePurposeRestrictedCookie() throws Exception {
        String name=username();registered(name);
        Browser recovery=new Browser();
        var challenge=recovery.post("/auth/password/forgot",Map.of("email",name+"@example.com"));
        assertThat(challenge.status()).isEqualTo(200);
        var mail=mailbox.await(name+"@example.com",EmailKind.PASSWORD_RESET);
        var csrf=proxy.send(forwarded("/auth/csrf","https://approved-tunnel.example").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String session=csrf.headers().firstValue("set-cookie").orElseThrow().split(";",2)[0];
        var token=json.readTree(csrf.body());
        var authorized=proxy.send(forwarded("/auth/password/verify","https://approved-tunnel.example")
                .header("Cookie",session).header(token.get("headerName").asText(),token.get("token").asText())
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "ticket",challenge.body().path("ticket").asText(),"code",mail.code())))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(authorized.statusCode()).isEqualTo(200);
        String grant=authorized.headers().allValues("set-cookie").stream()
                .filter(value->value.startsWith("HOOTLY_RESET=")).findFirst().orElseThrow();
        assertThat(grant).contains("Secure","HttpOnly","SameSite=Strict","Path=/api/auth/password")
                .doesNotContain("Domain=");
        var reset=proxy.send(forwarded("/auth/password/reset","https://approved-tunnel.example")
                .header("Cookie",session+"; "+grant.split(";",2)[0])
                .header(token.get("headerName").asText(),token.get("token").asText())
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "password","Recovered42!","confirmPassword","Recovered42!")))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(reset.statusCode()).isEqualTo(200);
        assertThat(reset.headers().allValues("set-cookie").stream()
                .filter(value->value.startsWith("HOOTLY_RESET=")).findFirst().orElseThrow())
                .contains("Secure","HttpOnly","SameSite=Strict","Max-Age=0");
        assertThat(new Browser().login(name,"Recovered42!").status()).isEqualTo(200);
    }
}
