package com.triviagame.online.auth;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final Accounts accounts;
    private final AccountDeletion deletion;
    private final LoginRateLimiter limiter;
    private final AccountEmailSecurity emailSecurity;
    public AuthController(Accounts accounts, AccountDeletion deletion, LoginRateLimiter limiter,AccountEmailSecurity emailSecurity) { this.accounts=accounts;this.deletion=deletion;this.limiter=limiter;this.emailSecurity=emailSecurity; }
    @GetMapping("/auth/csrf") Map<String,String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    VerificationChallenge register(@Valid @RequestBody Accounts.Registration input,HttpServletRequest request) { return emailSecurity.register(input,request.getRemoteAddr()); }
    @GetMapping("/me") Accounts.Profile me(Principal principal) { return accounts.profile(UUID.fromString(principal.getName())); }
    @PatchMapping("/me") Accounts.Profile edit(Principal principal, @Valid @RequestBody Accounts.EditProfile input) {
        return accounts.edit(UUID.fromString(principal.getName()), input);
    }
    @GetMapping("/health") Map<String,String> health() { return Map.of("status", "ok"); }
    @GetMapping("/auth/username-availability") Map<String,Object> usernameAvailability(@RequestParam String username,Principal principal){return accounts.usernameAvailability(username,principal==null?null:UUID.fromString(principal.getName()));}
    @PatchMapping("/me/avatar") Accounts.Profile avatar(Principal principal,@Valid @RequestBody AvatarConfig input){
        return accounts.avatar(UUID.fromString(principal.getName()),input);
    }
    @DeleteMapping("/account/me") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication, @Valid @RequestBody AccountDeletion.Confirmation input,
            HttpServletRequest request, HttpServletResponse response) {
        String owner=authentication.getName();
        if(!limiter.acquire("delete:"+owner))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many attempts. Try again in five minutes.");
        deletion.delete(UUID.fromString(owner),input);
        new SecurityContextLogoutHandler().logout(request,response,authentication);
        new CookieClearingLogoutHandler("QUIZ_SESSION").logout(request,response,authentication);
    }
}
