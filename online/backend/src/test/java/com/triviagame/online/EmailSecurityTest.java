package com.triviagame.online;

import com.triviagame.online.auth.*;
import com.triviagame.online.mail.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class EmailSecurityTest extends HttpTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountCodeSecrets secrets;
    @AfterEach void restoreMail(){mailbox.fail=false;}
    record Pending(Browser browser,String name,String email,String ticket,String code) { }
    Pending pending() throws Exception {String name=username(),email=name+"@example.com";Browser browser=new Browser();var result=browser.post("/auth/register",registration(name,email));assertThat(result.status()).isEqualTo(201);return new Pending(browser,name,email,result.body().get("ticket").asText(),mailbox.await(email,EmailKind.EMAIL_VERIFICATION).code());}
    Map<String,String> code(Pending p,String value){return Map.of("ticket",p.ticket(),"code",value);}
    @Test void pendingAccountCannotLoginAndCodesAreHashedPurposeBoundAndSingleUse() throws Exception {
        var p=pending();assertThat(p.code()).matches("[0-9]{6}");
        assertThat(p.browser.login(p.name,PASSWORD).status()).isEqualTo(401);
        var row=jdbc.queryForMap("SELECT * FROM account_email_challenges WHERE ticket_digest=?",secrets.digest(p.ticket));
        assertThat(row.get("code_digest").toString()).hasSize(64).isNotEqualTo(p.code);
        assertThat(row.get("ticket_digest")).isNotEqualTo(p.ticket);
        assertThat(p.browser.post("/auth/password/verify",code(p,p.code)).status()).isEqualTo(400);
        assertThat(p.browser.post("/auth/email/verify",code(p,p.code)).status()).isEqualTo(200);
        assertThat(p.browser.get("/me").body().get("emailVerified").asBoolean()).isTrue();
        assertThat(p.browser.post("/auth/email/verify",code(p,p.code)).status()).isEqualTo(400);
        assertThat(p.browser.login(p.name,PASSWORD).status()).isEqualTo(200);
    }
    @Test void incorrectAttemptsCommitAndLockAfterFive() throws Exception {
        var p=pending();String wrong=p.code.equals("000000")?"111111":"000000";
        for(int i=1;i<=5;i++)assertThat(p.browser.post("/auth/email/verify",code(p,wrong)).status()).isEqualTo(i==5?429:400);
        assertThat(jdbc.queryForObject("SELECT attempts FROM account_email_challenges WHERE ticket_digest=?",Integer.class,secrets.digest(p.ticket))).isEqualTo(5);
        assertThat(p.browser.post("/auth/email/verify",code(p,p.code)).status()).isEqualTo(429);
    }
    @Test void expiryCooldownAndNewCodeInvalidation() throws Exception {
        var p=pending();assertThat(p.browser.post("/auth/email/resend",Map.of("ticket",p.ticket)).status()).isEqualTo(429);
        clock.advance(Duration.ofMinutes(11));assertThat(p.browser.post("/auth/email/verify",code(p,p.code)).body().get("message").asText()).contains("expired");
        var next=p.browser.post("/auth/email/resend",Map.of("ticket",p.ticket));assertThat(next.status()).isEqualTo(200);
        assertThat(p.browser.post("/auth/email/verify",code(p,p.code)).status()).isEqualTo(400);
        var latest=mailbox.await(p.email,EmailKind.EMAIL_VERIFICATION);
        // Await a different delivery rather than assuming a new random code differs.
        long end=System.nanoTime()+Duration.ofSeconds(3).toNanos();while(latest.occurredAt().isBefore(clock.instant())&&System.nanoTime()<end){Thread.sleep(20);latest=mailbox.await(p.email,EmailKind.EMAIL_VERIFICATION);}
        assertThat(p.browser.post("/auth/email/verify",Map.of("ticket",next.body().get("ticket").asText(),"code",latest.code())).status()).isEqualTo(200);
    }
    @Test void resetRequiresGrantRevokesSessionsHashesPasswordAndDoesNotLogin() throws Exception {
        String name=username();Browser existing=registered(name),other=new Browser();assertThat(other.login(name,PASSWORD).status()).isEqualTo(200);
        Browser recovery=new Browser();var request=recovery.post("/auth/password/forgot",Map.of("email",name+"@example.com"));assertThat(request.status()).isEqualTo(200);
        var mail=mailbox.await(name+"@example.com",EmailKind.PASSWORD_RESET);String ticket=request.body().get("ticket").asText();
        assertThat(request.body().get("deliveryStatus").asText()).isEqualTo("ACCEPTED");
        var input=Map.of("password","NewPwd42","confirmPassword","NewPwd42");
        assertThat(recovery.post("/auth/password/reset",input).status()).isEqualTo(403);
        assertThat(recovery.post("/auth/password/verify",Map.of("ticket",ticket,"code",mail.code())).status()).isEqualTo(200);
        assertThat(recovery.cookies.getCookieStore().getCookies().toString()).contains("HOOTLY_RESET");
        assertThat(recovery.post("/auth/password/reset",input).status()).isEqualTo(200);
        assertThat(recovery.get("/me").status()).isEqualTo(401);assertThat(existing.get("/me").status()).isEqualTo(401);assertThat(other.get("/me").status()).isEqualTo(401);
        assertThat(recovery.post("/auth/password/reset",input).status()).isEqualTo(403);
        assertThat(recovery.login(name,PASSWORD).status()).isEqualTo(401);assertThat(recovery.login(name,"NewPwd42").status()).isEqualTo(200);
        String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE username=?",String.class,name);assertThat(passwords.matches("NewPwd42",hash)).isTrue();
        var notice=mailbox.await(name+"@example.com",EmailKind.PASSWORD_CHANGED);assertThat(notice.code()).isNull();
    }
    @Test void resetGrantExpiresAndUnknownEmailResponseIsIndistinguishable() throws Exception {
        String name=username();registered(name);Browser browser=new Browser();
        var known=browser.post("/auth/password/forgot",Map.of("email",name+"@example.com"));var unknown=browser.post("/auth/password/forgot",Map.of("email",username()+"@example.com"));
        assertThat(known.status()).isEqualTo(unknown.status());assertThat(known.body().get("deliveryStatus")).isEqualTo(unknown.body().get("deliveryStatus"));
        var mail=mailbox.await(name+"@example.com",EmailKind.PASSWORD_RESET);
        assertThat(browser.post("/auth/password/verify",Map.of("ticket",known.body().get("ticket").asText(),"code",mail.code())).status()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(6));assertThat(browser.post("/auth/password/reset",Map.of("password",PASSWORD,"confirmPassword",PASSWORD)).status()).isEqualTo(403);
    }
    @Test void settingsRequiresCurrentPasswordAndRecoveryAlwaysUsesOwnEmail() throws Exception {
        String name=username();Browser browser=registered(name);
        assertThat(browser.post("/account/security/password",Map.of("currentPassword","wrong","password",PASSWORD,"confirmPassword",PASSWORD)).status()).isEqualTo(403);
        var result=browser.post("/auth/password/forgot",Map.of("email","someoneelse@example.com"));assertThat(result.status()).isEqualTo(200);assertThat(result.body().get("maskedEmail").asText()).endsWith("@example.com");
        assertThat(mailbox.await(name+"@example.com",EmailKind.PASSWORD_RESET).to()).isEqualTo(name+"@example.com");
        assertThat(browser.post("/account/security/password",Map.of("currentPassword",PASSWORD,"password","Changed8","confirmPassword","Changed8")).status()).isEqualTo(200);
        assertThat(browser.get("/me").status()).isEqualTo(401);
    }
    @Test void legacyAccountsRemainUsableWithoutFalselyVerifiedEmail() throws Exception {
        var p=pending();jdbc.update("UPDATE users SET verification_required=FALSE WHERE username=?",p.name);
        assertThat(p.browser.login(p.name,PASSWORD).status()).isEqualTo(200);assertThat(p.browser.get("/me").body().get("emailVerified").asBoolean()).isFalse();
        assertThat(p.browser.post("/auth/email/verify",code(p,p.code)).body().get("authenticated").asBoolean()).isFalse();
        assertThat(p.browser.get("/me").body().get("emailVerified").asBoolean()).isTrue();
    }
    @Test void smtpFailureIsReportedWithoutExposingInternals() throws Exception {
        mailbox.fail=true;String name=username();Browser browser=new Browser();var result=browser.post("/auth/register",registration(name,name+"@example.com"));assertThat(result.status()).isEqualTo(201);
        Result state=null;long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();do{state=browser.post("/auth/email/status",Map.of("ticket",result.body().get("ticket").asText()));if(state.body().get("deliveryStatus").asText().equals("FAILED"))break;Thread.sleep(20);}while(System.nanoTime()<end);
        assertThat(state.body().get("deliveryStatus").asText()).isEqualTo("FAILED");assertThat(state.body().toString()).doesNotContain("SMTP","password","code_digest");
    }
    @Test void keyedDigestsAreAccountAndPurposeBound(){UUID first=UUID.randomUUID(),second=UUID.randomUUID();String code=secrets.code();assertThat(secrets.codeDigest(first,first,"EMAIL_VERIFICATION",code)).isNotEqualTo(secrets.codeDigest(first,second,"EMAIL_VERIFICATION",code)).isNotEqualTo(secrets.codeDigest(first,first,"PASSWORD_RESET",code));}
    @Test void exactAccountRecipientAndNameSurviveRecoveryWithoutAutomaticOrDuplicateMail() throws Exception {
        String name=username(),email=name+"+chosen@example.com";Browser browser=new Browser();
        var input=new HashMap<String,Object>(registration(name,email));input.put("displayName","Justine & Friends");
        var pending=browser.post("/auth/register",input);assertThat(pending.status()).isEqualTo(201);
        var first=mailbox.await(email,EmailKind.EMAIL_VERIFICATION);
        assertThat(first.to()).isEqualTo(email);assertThat(first.displayName()).isEqualTo("Justine & Friends");
        for(int i=0;i<3;i++)assertThat(browser.post("/auth/email/status",Map.of("ticket",pending.body().get("ticket").asText())).status()).isEqualTo(200);
        assertThat(browser.post("/auth/email/resend",Map.of("ticket",pending.body().get("ticket").asText())).status()).isEqualTo(429);
        verifyRegistration(browser,pending,email);assertThat(browser.login(name,PASSWORD).status()).isEqualTo(200);
        for(int i=0;i<3;i++){assertThat(browser.get("/me").status()).isEqualTo(200);assertThat(browser.get("/health").status()).isEqualTo(200);browser.get("/auth/csrf");}
        UUID user=jdbc.queryForObject("SELECT id FROM users WHERE username=?",UUID.class,name);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_email_deliveries WHERE user_id=?",Integer.class,user)).isEqualTo(1);
        var recovery=browser.post("/auth/password/forgot",Map.of("email","another@example.com"));assertThat(recovery.status()).isEqualTo(200);
        assertThat(mailbox.await(email,EmailKind.PASSWORD_RESET).to()).isEqualTo(email);
        assertThat(browser.post("/auth/password/forgot",Map.of("email",email)).status()).isEqualTo(429);
        assertThat(browser.post("/account/security/password",Map.of("currentPassword","wrong","password","Changed8","confirmPassword","Changed8")).status()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_email_deliveries WHERE user_id=?",Integer.class,user)).isEqualTo(2);
        assertThat(browser.post("/account/security/password",Map.of("currentPassword",PASSWORD,"password","Changed8","confirmPassword","Changed8")).status()).isEqualTo(200);
        var notice=mailbox.await(email,EmailKind.PASSWORD_CHANGED);assertThat(notice.to()).isEqualTo(email);assertThat(notice.displayName()).isEqualTo("Justine & Friends");
    }
}
