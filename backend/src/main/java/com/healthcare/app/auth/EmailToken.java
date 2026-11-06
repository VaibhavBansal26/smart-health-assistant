package com.healthcare.app.auth;

import com.healthcare.app.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single-use link token mailed to a user (verify their email, or set/reset their password).
 * Only a SHA-256 hash is stored, so a database leak cannot be turned into working links.
 */
@Entity
@Table(name = "email_tokens", indexes = @Index(name = "idx_email_token_user", columnList = "user_id, type"))
public class EmailToken {

    public enum Type { VERIFY_EMAIL, PASSWORD_RESET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected EmailToken() {
    }

    public EmailToken(User user, Type type, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.type = type;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public void markUsed(Instant now) { this.usedAt = now; }

    public User getUser() { return user; }
    public Type getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
