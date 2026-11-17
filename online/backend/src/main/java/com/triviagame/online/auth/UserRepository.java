package com.triviagame.online.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsernameOrEmail(String username, String email);
    boolean existsByUsernameOrEmail(String username, String email);
    boolean existsByUsername(String username);
    boolean existsByUsernameAndIdNot(String username,UUID id);
    boolean existsByIdAndDeletedAtIsNull(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id=:id")
    Optional<UserAccount> lockById(@Param("id") UUID id);
}
