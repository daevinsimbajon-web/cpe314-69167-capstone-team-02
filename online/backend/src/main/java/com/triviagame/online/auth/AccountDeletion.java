package com.triviagame.online.auth;

import com.triviagame.online.rooms.RoomService;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountDeletion {
    public record Confirmation(@NotBlank @Size(max=64) String password,
                               @NotNull @Pattern(regexp="DELETE") String confirmation) { }
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final RoomService rooms;
    private final Clock clock;
    public AccountDeletion(UserRepository users, PasswordEncoder passwords, JdbcTemplate jdbc, RoomService rooms, Clock clock) {
        this.users=users;this.passwords=passwords;this.jdbc=jdbc;this.rooms=rooms;this.clock=clock;
    }
    /** Remove identity and credentials, retaining only anonymous game references. */
    @Transactional
    public void delete(UUID owner, Confirmation input) {
        UserAccount account=users.lockById(owner).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if(account.deletedAt!=null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72
                ||!passwords.matches(input.password(),account.passwordHash))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Your password is incorrect. Your account has not been deleted.");
        rooms.retireAccount(owner);
        jdbc.update("UPDATE question_bank_imports SET user_id=NULL,source='Imported questions' WHERE user_id=?",owner);
        account.displayName="Deleted player";
        account.avatarConfig=null;
        account.username="deleted_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        account.email=owner+"@deleted.invalid";
        account.passwordHash=passwords.encode(UUID.randomUUID().toString());
        account.deletedAt=clock.instant();
        users.flush();
        // Spring Session's attributes use ON DELETE CASCADE. This revokes every device.
        jdbc.update("DELETE FROM spring_session WHERE principal_name=?",owner.toString());
    }
}
