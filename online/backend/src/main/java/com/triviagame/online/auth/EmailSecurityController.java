package com.triviagame.online.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.*;
import java.security.Principal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.*;
import org.springframework.security.web.authentication.logout.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class EmailSecurityController {
    private final AccountEmailSecurity security;private final Accounts accounts;private final boolean secureCookie;
    public EmailSecurityController(AccountEmailSecurity security,Accounts accounts,@Value("${app.cookie-secure:false}") boolean secureCookie){this.security=security;this.accounts=accounts;this.secureCookie=secureCookie;}
    public record Ticket(@NotBlank @Size(max=64) String ticket) { }
    public record Resend(@NotBlank @Size(max=64) String ticket,@Email @Size(max=254) String email) { }
    public record EmailAddress(@NotBlank @Email @Size(max=254) String email) { }
    public record SignIn(@NotBlank @Size(max=254) String identifier,@NotBlank @Size(max=64) String password) { }
    @ModelAttribute public void noCache(HttpServletResponse response){response.setHeader("Cache-Control","no-store, private");}
    @PostMapping("/auth/email/start") public VerificationChallenge start(@Valid @RequestBody SignIn input,HttpServletRequest request){return security.startVerification(input.identifier(),input.password(),request.getRemoteAddr());}
    @PostMapping("/auth/email/status") public VerificationChallenge status(@Valid @RequestBody Ticket input){return security.status(input.ticket());}
    @PostMapping("/auth/email/resend") public VerificationChallenge resend(@Valid @RequestBody Resend input,HttpServletRequest request){return security.resend(input.ticket(),input.email(),request.getRemoteAddr());}
    @PostMapping("/auth/email/verify") public Map<String,Object> verify(@Valid @RequestBody EmailCodeRequest input,HttpServletRequest request,HttpServletResponse response){
        var result=security.verify(input,"EMAIL_VERIFICATION",request.getRemoteAddr());
        if(result.newlyActivated()){
            var profile=accounts.profile(result.user());var details=accounts.loadUserByUsername(profile.username());
            var authentication=UsernamePasswordAuthenticationToken.authenticated(details,null,details.getAuthorities());
            if(request.getSession(false)!=null)request.changeSessionId();
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);SecurityContextHolder.setContext(context);new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        }
        return Map.of("verified",true,"authenticated",result.newlyActivated());
    }
    @PostMapping("/auth/password/forgot") public VerificationChallenge forgot(@Valid @RequestBody EmailAddress input,Principal principal,HttpServletRequest request){return security.requestReset(input.email(),id(principal),request.getRemoteAddr());}
    @PostMapping("/auth/password/verify") public Map<String,Object> verifyReset(@Valid @RequestBody EmailCodeRequest input,HttpServletRequest request,HttpServletResponse response){
        var checked=security.verify(input,"PASSWORD_RESET",request.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("HOOTLY_RESET",checked.resetGrant()).path("/api/auth/password").httpOnly(true).secure(secureCookie||request.isSecure()).sameSite("Strict").maxAge(300).build().toString());
        return Map.of("authorized",true,"expiresAt",checked.expiresAt());
    }
    @PostMapping("/auth/password/reset") public Map<String,Boolean> reset(@CookieValue(name="HOOTLY_RESET",required=false) String grant,@Valid @RequestBody PasswordResetRequest input,Principal principal,HttpServletRequest request,HttpServletResponse response){
        security.reset(grant,input,id(principal));endSession(request,response);return Map.of("passwordChanged",true);
    }
    @PostMapping("/account/security/email/verification") public VerificationChallenge ownVerification(Principal user,HttpServletRequest request){return security.requestVerification(id(user),request.getRemoteAddr());}
    @PostMapping("/account/security/password/forgot") public VerificationChallenge ownReset(Principal user,HttpServletRequest request){return security.requestReset("",id(user),request.getRemoteAddr());}
    @PostMapping("/account/security/password") public Map<String,Boolean> change(Principal user,@Valid @RequestBody PasswordChangeRequest input,HttpServletRequest request,HttpServletResponse response){security.changePassword(id(user),input,request.getRemoteAddr());endSession(request,response);return Map.of("passwordChanged",true);}
    private UUID id(Principal principal){return principal==null?null:UUID.fromString(principal.getName());}
    private void endSession(HttpServletRequest request,HttpServletResponse response){new SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());new CookieClearingLogoutHandler("QUIZ_SESSION").logout(request,response,null);response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("HOOTLY_RESET","").path("/api/auth/password").httpOnly(true).secure(secureCookie||request.isSecure()).sameSite("Strict").maxAge(0).build().toString());}
}
