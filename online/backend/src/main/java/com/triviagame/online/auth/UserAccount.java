package com.triviagame.online.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id UUID id;
    @Column(name = "display_name", nullable = false, length = 60) String displayName;
    @Column(nullable = false, unique = true, length = 24) String username;
    @Column(nullable = false, unique = true, length = 254) String email;
    @Column(name = "password_hash", nullable = false, length = 100) String passwordHash;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "avatar_config",length=500) String avatarConfig;
    @Column(name = "email_verified",nullable=false) boolean emailVerified;
    @Column(name = "verification_required",nullable=false) boolean verificationRequired;
    @Column(name = "email_verified_at") Instant emailVerifiedAt;
    protected UserAccount() { }
    UserAccount(String displayName, String username, String email, String hash, Instant now) {
        this.id = UUID.randomUUID(); this.displayName = displayName; this.username = username;
        this.email = email; this.passwordHash = hash; this.createdAt = now;
        this.avatarConfig=AvatarConfig.forNewUser(this.id).stored();
        this.verificationRequired=true;
    }
    public UUID getId() { return id; }
    public String getPasswordHash() { return passwordHash; }
}
