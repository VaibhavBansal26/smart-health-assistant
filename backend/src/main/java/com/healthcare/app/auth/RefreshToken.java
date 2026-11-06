package com.healthcare.app.auth;

import com.healthcare.app.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A long-lived login session, stored only as a SHA-256 hash (a database leak therefore cannot be replayed as cookies).
 * Tokens form a "family": each refresh revokes the used token and issues the next one in the same family, so if an
 * old token is ever replayed (stolen cookie) the whole family can be revoked.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_family", columnList = "family_id"),
        @Index(name = "idx_refresh_user", columnList = "user_id")})
public class RefreshToken {

    public static final String ROTATED = "ROTATED";
    public static final String LOGOUT = "LOGOUT";
    public static final String REUSE_DETECTED = "REUSE_DETECTED";
    public static final String CREDENTIALS_CHANGED = "CREDENTIALS_CHANGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(length = 30)
    private String revokedReason;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, String familyId, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public void revoke(String reason, Instant now) {
        this.revokedAt = now;
        this.revokedReason = reason;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public String getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedReason() { return revokedReason; }
}
