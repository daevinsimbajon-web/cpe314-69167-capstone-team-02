package com.triviagame.online.auth;

import com.triviagame.online.analytics.AnalyticsService;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Accounts implements UserDetailsService {
    public record Registration(
            @NotBlank @Size(min=1, max=40) String displayName,
            @NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,24}") String username,
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Pattern(regexp="(?s)(?=.*[A-Za-z])(?=.*[0-9]).{8,64}",
                    message="Use 8–64 characters including a letter and number") String password,
            @NotBlank String confirmPassword) {
        @AssertTrue(message="Passwords must match")
        public boolean isPasswordConfirmed() { return password != null && password.equals(confirmPassword); }
    }
    public record Profile(UUID id, String displayName, String username, String email, Instant createdAt,
                          Map<String, Object> statistics,AvatarConfig avatar,boolean emailVerified,boolean verificationRequired) { }
    public record EditProfile(@NotBlank @Size(min=1,max=40) String displayName,@Pattern(regexp="[A-Za-z0-9_]{3,24}") String username) {
        public EditProfile(String displayName){this(displayName,null);}
    }
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final AnalyticsService analytics;
    private final org.springframework.context.ApplicationEventPublisher events;
    public Accounts(UserRepository users, PasswordEncoder passwords, Clock clock, AnalyticsService analytics,org.springframework.context.ApplicationEventPublisher events) {
        this.users=users; this.passwords=passwords; this.clock=clock; this.analytics=analytics;this.events=events;
    }
    @Transactional
    public Profile register(Registration input) {
        String username = canonical(input.username()), email = canonical(input.email());
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
        if (users.existsByUsernameOrEmail(username, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already registered");
        }
        UserAccount user = users.saveAndFlush(new UserAccount(input.displayName().strip(), username,
                email, passwords.encode(input.password()), clock.instant()));
        return profile(user.id);
    }
    @Override
    public UserDetails loadUserByUsername(String identifier) {
        UserAccount user = users.findByUsernameOrEmail(canonical(identifier), canonical(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        if (user.deletedAt != null) throw new UsernameNotFoundException("Invalid credentials");
        return User.withUsername(user.id.toString()).password(user.passwordHash).roles("USER").disabled(user.verificationRequired&&!user.emailVerified).build();
    }
    @Transactional(readOnly=true)
    public Profile profile(UUID id) {
        UserAccount u = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (u.deletedAt != null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to continue");
        Map<String,Object> stats = analytics.statistics(id);
        return new Profile(u.id, u.displayName, u.username, u.email, u.createdAt, stats,AvatarConfig.read(u.avatarConfig,u.id),u.emailVerified,u.verificationRequired);
    }
    @Transactional
    public Profile edit(UUID id, EditProfile input) {
        UserAccount u = users.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (u.deletedAt != null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(input.username()!=null){
            String next=canonical(input.username());
            if(users.existsByUsernameAndIdNot(next,id))throw new ResponseStatusException(HttpStatus.CONFLICT,"That username is already taken.");
            u.username=next;
        }
        u.displayName = input.displayName().strip();
        users.flush();
        events.publishEvent(new AccountProfileChanged(id));
        return profile(id);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> usernameAvailability(String value,UUID current){
        boolean valid=value!=null&&value.matches("[A-Za-z0-9_]{3,24}");
        boolean available=valid&&(current==null?!users.existsByUsername(value.toLowerCase(Locale.ROOT)):!users.existsByUsernameAndIdNot(value.toLowerCase(Locale.ROOT),current));
        return Map.of("available",available,"valid",valid);
    }
    private static String canonical(String input) { return input.strip().toLowerCase(Locale.ROOT); }
    @Transactional
    public Profile avatar(UUID id,AvatarConfig avatar){
        if(avatar==null||!avatar.valid())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid owl avatar");
        UserAccount user=users.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if(user.deletedAt!=null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        user.avatarConfig=avatar.stored();return profile(id);
    }
    @Transactional(readOnly=true)
    public boolean isActive(UUID id) { return users.findById(id).filter(u->u.deletedAt==null&&(!u.verificationRequired||u.emailVerified)).isPresent(); }
}
