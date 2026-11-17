package com.triviagame.online.auth;

import com.triviagame.online.mail.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountEmailSecurity {
    private final JdbcTemplate jdbc;private final Accounts accounts;private final AccountCodeSecrets secrets;private final EmailDelivery mail;
    private final PasswordEncoder passwords;private final Clock clock;private final LoginRateLimiter limiter;private final TransactionTemplate tx;private final ApplicationEventPublisher events;
    private record Account(UUID id,String name,String email,String hash,boolean verified,boolean required) { }
    private record Challenge(UUID id,UUID user,String purpose,String email,String digest,Instant expires,Instant resend,int attempts,Instant consumed,String delivery) { }
    public record Checked(UUID user,boolean newlyActivated,String resetGrant,Instant expiresAt) { }
    private record Decision(Checked checked,String message,HttpStatus status) { }
    public AccountEmailSecurity(JdbcTemplate jdbc,Accounts accounts,AccountCodeSecrets secrets,EmailDelivery mail,PasswordEncoder passwords,Clock clock,LoginRateLimiter limiter,PlatformTransactionManager manager,ApplicationEventPublisher events){
        this.jdbc=jdbc;this.accounts=accounts;this.secrets=secrets;this.mail=mail;this.passwords=passwords;this.clock=clock;this.limiter=limiter;this.tx=new TransactionTemplate(manager);this.events=events;
    }
    public VerificationChallenge register(Accounts.Registration input,String ip){
        ready();rate("email-send-ip:"+ip);var profile=accounts.register(input);return tx.execute(status->issue(account(profile.id()),profile.email(),"EMAIL_VERIFICATION"));
    }
    public VerificationChallenge startVerification(String identifier,String password,String ip){
        ready();rate("email-start-ip:"+ip);
        Account account=jdbc.query("SELECT id,display_name,email,password_hash,email_verified,verification_required FROM users WHERE deleted_at IS NULL AND (username=? OR email=?)",(rs,n)->new Account(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getBoolean(6)),canonical(identifier),canonical(identifier)).stream().findFirst().orElse(null);
        if(account==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!passwords.matches(password,account.hash()))throw error(HttpStatus.UNAUTHORIZED,"Check your sign-in details and try again.");
        return tx.execute(status->issue(account(account.id()),account.email(),"EMAIL_VERIFICATION"));
    }
    public VerificationChallenge requestVerification(UUID user,String ip){ready();rate("email-send-ip:"+ip);return tx.execute(status->{var account=account(user);return issue(account,account.email(),"EMAIL_VERIFICATION");});}
    public VerificationChallenge requestReset(String email,UUID signedIn,String ip){
        ready();rate("email-send-ip:"+ip);String address=canonical(email);
        return tx.execute(status->{
            Account account=signedIn==null?jdbc.query("SELECT id,display_name,email,password_hash,email_verified,verification_required FROM users WHERE email=? AND deleted_at IS NULL",(rs,n)->new Account(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getBoolean(6)),address).stream().findFirst().orElse(null):account(signedIn);
            if(account!=null)account=account(account.id());
            return issue(account,account==null?address:account.email(),"PASSWORD_RESET");
        });
    }
    public VerificationChallenge status(String ticket){return tx.execute(status->view(challenge(ticket,false),ticket));}
    public VerificationChallenge resend(String ticket,String changedEmail,String ip){
        ready();rate("email-send-ip:"+ip);
        return tx.execute(status->{
            var prior=challenge(ticket,false);Account account=prior.user()==null?null:account(prior.user());
            if(prior.resend().isAfter(clock.instant()))throw error(HttpStatus.TOO_MANY_REQUESTS,"Wait for the countdown before requesting another code.");
            String address=prior.email();
            if(changedEmail!=null){
                if(!prior.purpose().equals("EMAIL_VERIFICATION")||account==null||!account.required()||account.verified())throw error(HttpStatus.FORBIDDEN,"Email changes are only available during new-account verification.");
                address=canonical(changedEmail);if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=? AND id<>?",Integer.class,address,account.id())>0)throw error(HttpStatus.CONFLICT,"That email is already registered.");
                jdbc.update("UPDATE users SET email=? WHERE id=?",address,account.id());account=new Account(account.id(),account.name(),address,account.hash(),false,true);
            }
            return issue(account,address,prior.purpose());
        });
    }
    private VerificationChallenge issue(Account account,String address,String purpose){
        rate("email-send-account:"+address);
        UUID user=account==null?null:account.id();
        if(purpose.equals("EMAIL_VERIFICATION")&&account!=null&&account.verified())throw error(HttpStatus.CONFLICT,"Your email is already verified. Sign in to continue.");
        var recent=jdbc.query("SELECT resend_at FROM account_email_challenges WHERE email_address=? AND purpose=? ORDER BY created_at DESC LIMIT 1",(rs,n)->rs.getObject(1,OffsetDateTime.class).toInstant(),address,purpose);
        if(!recent.isEmpty()&&recent.get(0).isAfter(clock.instant()))throw error(HttpStatus.TOO_MANY_REQUESTS,"Wait for the countdown before requesting another code.");
        UUID id=UUID.randomUUID();String ticket=secrets.token(),code=secrets.code();Instant expires=clock.instant().plus(Duration.ofMinutes(10)),resend=clock.instant().plusSeconds(60);
        if(user!=null){jdbc.update("UPDATE account_email_challenges SET consumed_at=? WHERE user_id=? AND purpose=? AND consumed_at IS NULL",now(),user,purpose);if(purpose.equals("PASSWORD_RESET"))jdbc.update("UPDATE password_reset_grants SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL",now(),user);}
        jdbc.update("INSERT INTO account_email_challenges(id,user_id,ticket_digest,purpose,email_address,code_digest,expires_at,resend_at,created_at) VALUES(?,?,?,?,?,?,?,?,?)",id,user,secrets.digest(ticket),purpose,address,secrets.codeDigest(id,user,purpose,code),at(expires),at(resend),now());
        if(account!=null)mail.queue(user,id,address,account.name(),EmailKind.valueOf(purpose),code);
        return new VerificationChallenge(ticket,mask(address),expires,resend,purpose,purpose.equals("PASSWORD_RESET")?"ACCEPTED":"QUEUED");
    }
    public Checked verify(EmailCodeRequest request,String purpose,String ip){
        if(!secrets.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"Email verification is not configured yet.");rate("email-check-ip:"+ip);
        // A decision is returned from the transaction before an HTTP error is thrown:
        // failed attempts therefore commit instead of rolling back with an exception.
        Decision decision=tx.execute(status->{
            Challenge initial=challenge(request.ticket(),false);if(initial.user()!=null)account(initial.user());
            Challenge challenge=challenge(request.ticket(),true);
            if(!challenge.purpose().equals(purpose))return rejected("That code doesn't match. Try again.",HttpStatus.BAD_REQUEST);
            if(challenge.consumed()!=null)return rejected("This code has already been used. Request a new one.",HttpStatus.BAD_REQUEST);
            if(!challenge.expires().isAfter(clock.instant()))return rejected("Your code has expired. Request a new one.",HttpStatus.BAD_REQUEST);
            if(challenge.attempts()>=5)return rejected("Too many attempts. Please request a new code.",HttpStatus.TOO_MANY_REQUESTS);
            if(!secrets.matches(challenge.digest(),secrets.codeDigest(challenge.id(),challenge.user(),purpose,request.code()))||challenge.user()==null){
                jdbc.update("UPDATE account_email_challenges SET attempts=attempts+1 WHERE id=?",challenge.id());
                return rejected(challenge.attempts()==4?"Too many attempts. Please request a new code.":"That code doesn't match. Try again.",challenge.attempts()==4?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.BAD_REQUEST);
            }
            Account account=account(challenge.user());jdbc.update("UPDATE account_email_challenges SET consumed_at=? WHERE id=?",now(),challenge.id());
            if(purpose.equals("EMAIL_VERIFICATION")){
                jdbc.update("UPDATE users SET email_verified=TRUE,email_verified_at=? WHERE id=?",now(),account.id());
                return new Decision(new Checked(account.id(),account.required(),null,null),null,null);
            }
            String grant=secrets.token();Instant expires=clock.instant().plus(Duration.ofMinutes(5));
            jdbc.update("UPDATE password_reset_grants SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL",now(),account.id());
            jdbc.update("INSERT INTO password_reset_grants(id,user_id,grant_digest,expires_at) VALUES(?,?,?,?)",UUID.randomUUID(),account.id(),secrets.digest(grant),at(expires));
            return new Decision(new Checked(account.id(),false,grant,expires),null,null);
        });
        if(decision.checked()==null)throw error(decision.status(),decision.message());return decision.checked();
    }
    public void reset(String grant,PasswordResetRequest input,UUID signedIn){
        validatePassword(input.password(),input.confirmPassword());
        tx.executeWithoutResult(status->{
            String digest=secrets.digest(grant==null?"":grant);
            var initial=jdbc.queryForList("SELECT user_id FROM password_reset_grants WHERE grant_digest=? AND purpose='PASSWORD_RESET'",digest);
            if(initial.isEmpty())throw error(HttpStatus.FORBIDDEN,"Your reset session has expired. Request a new code.");
            UUID intended=(UUID)initial.get(0).get("user_id");account(intended);
            if(signedIn!=null&&!signedIn.equals(intended))throw error(HttpStatus.FORBIDDEN,"Start password recovery for your current account.");
            var rows=jdbc.queryForList("SELECT * FROM password_reset_grants WHERE grant_digest=? AND purpose='PASSWORD_RESET' FOR UPDATE",digest);
            if(rows.isEmpty()||rows.get(0).get("consumed_at")!=null||!instant(rows.get(0).get("expires_at")).isAfter(clock.instant()))throw error(HttpStatus.FORBIDDEN,"Your reset session has expired. Request a new code.");
            UUID user=(UUID)rows.get(0).get("user_id");Account account=account(user);
            change(account,input.password());
        });
    }
    public void changePassword(UUID user,PasswordChangeRequest input,String ip){
        rate("password-change:"+user+":"+ip);validatePassword(input.password(),input.confirmPassword());
        tx.executeWithoutResult(status->{var account=account(user);if(input.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!passwords.matches(input.currentPassword(),account.hash()))throw error(HttpStatus.FORBIDDEN,"Your current password doesn't match.");change(account,input.password());});
    }
    private void change(Account account,String password){
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?",passwords.encode(password),account.id());
        jdbc.update("UPDATE password_reset_grants SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL",now(),account.id());
        jdbc.update("UPDATE account_email_challenges SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL",now(),account.id());
        jdbc.update("DELETE FROM spring_session WHERE principal_name=?",account.id().toString());events.publishEvent(new AccountCredentialsChanged(account.id()));
        mail.queue(account.id(),null,account.email(),account.name(),EmailKind.PASSWORD_CHANGED,null);
    }
    private Account account(UUID id){return jdbc.query("SELECT id,display_name,email,password_hash,email_verified,verification_required FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE",(rs,n)->new Account(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getBoolean(6)),id).stream().findFirst().orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Please sign in to continue."));}
    private Challenge challenge(String ticket,boolean lock){
        if(ticket==null||ticket.length()>64)throw error(HttpStatus.BAD_REQUEST,"Start a new verification request.");
        return jdbc.query("SELECT id,user_id,purpose,email_address,code_digest,expires_at,resend_at,attempts,consumed_at,delivery_status FROM account_email_challenges WHERE ticket_digest=?"+(lock?" FOR UPDATE":""),(rs,n)->new Challenge(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getObject(6,OffsetDateTime.class).toInstant(),rs.getObject(7,OffsetDateTime.class).toInstant(),rs.getInt(8),rs.getObject(9,OffsetDateTime.class)==null?null:rs.getObject(9,OffsetDateTime.class).toInstant(),rs.getString(10)),secrets.digest(ticket)).stream().findFirst().orElseThrow(()->error(HttpStatus.BAD_REQUEST,"Start a new verification request."));
    }
    private VerificationChallenge view(Challenge challenge,String ticket){return new VerificationChallenge(ticket,mask(challenge.email()),challenge.expires(),challenge.resend(),challenge.purpose(),challenge.purpose().equals("PASSWORD_RESET")?"ACCEPTED":challenge.delivery());}
    private void ready(){if(!secrets.configured()||!mail.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"We couldn't send your code. Please try again.");}
    private void rate(String key){if(!limiter.acquire(key))throw error(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Please try again in five minutes.");}
    private Decision rejected(String message,HttpStatus status){return new Decision(null,message,status);}
    private static void validatePassword(String password,String confirmation){if(!password.equals(confirmation))throw error(HttpStatus.BAD_REQUEST,"Your passwords don't match.");if(!password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).{8,64}")||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw error(HttpStatus.BAD_REQUEST,"Use 8–64 characters including a letter and number (up to 72 UTF-8 bytes).");}
    private static String canonical(String value){return value.strip().toLowerCase(Locale.ROOT);}
    private static String mask(String email){int at=email.indexOf('@');return at<1?"your email":email.substring(0,1)+"•••"+email.substring(at);}
    private static Instant instant(Object value){return com.triviagame.online.DatabaseTimes.instant(value);}
    private OffsetDateTime now(){return at(clock.instant());}private OffsetDateTime at(Instant value){return value.atOffset(ZoneOffset.UTC);}
    private static ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
