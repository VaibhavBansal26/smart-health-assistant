package com.healthcare.app.auth;

import com.healthcare.app.exception.ApiException;
import com.healthcare.app.user.User;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Issues, rotates and revokes refresh tokens. See {@link RefreshToken} for the model. */
@Service
public class RefreshTokenService {

    public record Issued(String rawToken, User user) {
    }

    private final RefreshTokenRepository repo;
    private final Duration lifetime;
    private final Duration reuseGrace;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo,
                               @Value("${app.auth.refresh-token-days:14}") long days,
                               @Value("${app.auth.refresh-reuse-grace-seconds:10}") long graceSeconds) {
        this.repo = repo;
        this.lifetime = Duration.ofDays(days);
        this.reuseGrace = Duration.ofSeconds(graceSeconds);
    }

    /** Starts a new session (new family) for a user who just proved who they are. */
    @Transactional
    public Issued issue(User user) {
        return create(user, UUID.randomUUID().toString());
    }

    /**
     * Exchanges a refresh token for the next one.
     *
     * Presenting a token that was ALREADY used means either a stolen cookie or two tabs refreshing at once. Within a
     * short grace window (default 10s) it is treated as the latter and a sibling token is issued; beyond it the whole
     * family is revoked, signing out both the thief and the victim, who can simply log in again.
     *
     * noRollbackFor: revoking the family must be committed even though we then throw 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken token = repo.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again."));

        if (!token.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again.");
        }
        if (token.getRevokedAt() != null) {
            boolean recentlyRotated = RefreshToken.ROTATED.equals(token.getRevokedReason())
                    && token.getRevokedAt().plus(reuseGrace).isAfter(now);
            if (!recentlyRotated) {
                repo.revokeFamily(token.getFamilyId(), now, RefreshToken.REUSE_DETECTED);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again.");
            }
            // benign concurrent refresh: fall through and issue a sibling in the same family
        } else {
            token.revoke(RefreshToken.ROTATED, now);
        }
        // token.getUser() is a lazy proxy; the caller uses it after this transaction ends, so load the real entity now
        return create((User) Hibernate.unproxy(token.getUser()), token.getFamilyId());
    }

    /** Logout: ends this session only. Unknown or already-revoked tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repo.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                repo.revokeFamily(t.getFamilyId(), Instant.now(), RefreshToken.LOGOUT);
            }
        });
    }

    /** Signs a user out everywhere, e.g. after a password reset. */
    @Transactional
    public void revokeAllFor(Long userId, String reason) {
        repo.revokeAllForUser(userId, Instant.now(), reason);
    }

    /** Housekeeping: drop tokens that expired more than a day ago. */
    @Scheduled(cron = "${app.auth.refresh-purge-cron:0 17 3 * * *}")
    @Transactional
    public void purgeExpired() {
        repo.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }

    private Issued create(User user, String familyId) {
        byte[] bytes = new byte[32]; // 256 bits of randomness
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repo.save(new RefreshToken(user, hash(raw), familyId, Instant.now().plus(lifetime)));
        return new Issued(raw, user);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
