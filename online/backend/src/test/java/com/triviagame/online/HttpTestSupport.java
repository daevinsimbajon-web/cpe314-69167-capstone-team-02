package com.triviagame.online;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(HttpTestSupport.TimeConfiguration.class)
abstract class HttpTestSupport {
    static final String PASSWORD="ExamplePassword42!";
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired CapturedMailbox mailbox;
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary CapturedMailbox testMailbox() { return new CapturedMailbox(); }
    }
    static class MutableClock extends Clock {
        private Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        public synchronized void advance(Duration duration) { now=now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public synchronized Instant instant() { return now; }
    }
    record Result(int status, JsonNode body, HttpHeaders headers) { }
    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        Result get(String path) throws Exception { return send("GET",path,null,false,false); }
        Result post(String path,Object body) throws Exception {
            if(path.startsWith("/rooms/")&&path.endsWith("/answers")&&body instanceof Map<?,?> values&&!values.containsKey("submissionId")) {
                var withId=new java.util.LinkedHashMap<Object,Object>(values);withId.put("submissionId",UUID.randomUUID());body=withId;
            }
            return send("POST",path,body,false,true);
        }
        Result patch(String path,Object body) throws Exception { return send("PATCH",path,body,false,true); }
        Result delete(String path) throws Exception { return send("DELETE",path,null,false,true); }
        Result login(String name,String password) throws Exception {
            return send("POST","/auth/login","username="+URLEncoder.encode(name,StandardCharsets.UTF_8)
                    +"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8),true,true);
        }
        Result send(String method,String path,Object body,boolean form,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).timeout(Duration.ofSeconds(10));
            if (csrf) {
                Result token=get("/auth/csrf"); assertThat(token.status()).isEqualTo(200);
                request.header(token.body().get("headerName").asText(),token.body().get("token").asText());
            }
            if(body!=null) request.header("Content-Type",form?"application/x-www-form-urlencoded":"application/json");
            String content=body==null?"":form?(String)body:json.writeValueAsString(body);
            var response=client.send(request.method(method,HttpRequest.BodyPublishers.ofString(content)).build(),HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(),response.body().isBlank()?json.nullNode():json.readTree(response.body()),response.headers());
        }
    }
    String username() { return "user_"+UUID.randomUUID().toString().replace("-","").substring(0,12); }
    Map<String,Object> registration(String name,String email) {
        return Map.of("displayName","Quiz Player","username",name,"email",email,"password",PASSWORD,"confirmPassword",PASSWORD);
    }
    Browser registered(String name) throws Exception {
        Browser browser=new Browser();
        Result pending=browser.post("/auth/register",registration(name,name+"@example.com"));
        assertThat(pending.status()).isEqualTo(201);
        verifyRegistration(browser,pending,name+"@example.com");
        assertThat(browser.login(name,PASSWORD).status()).isEqualTo(200);
        return browser;
    }
    void verifyRegistration(Browser browser,Result pending,String email) throws Exception {
        var delivered=mailbox.await(email,com.triviagame.online.mail.EmailKind.EMAIL_VERIFICATION);
        assertThat(browser.post("/auth/email/verify",Map.of("ticket",pending.body().get("ticket").asText(),"code",delivered.code())).status()).isEqualTo(200);
    }
}
