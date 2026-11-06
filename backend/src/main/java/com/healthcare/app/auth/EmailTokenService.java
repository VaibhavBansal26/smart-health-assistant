package com.healthcare.app.auth;

import com.healthcare.app.exception.ApiException;
import com.healthcare.app.user.User;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class EmailTokenService {

    private final EmailTokenRepository repo;
    private final SecureRandom random = new SecureRandom();

    public EmailTokenService(EmailTokenRepository repo) {
        this.repo = repo;
    }

    /** Creates a fresh token and invalidates any earlier unused one of the same kind for this user. */
    @Transactional
    public String issue(User user, EmailToken.Type type, Duration ttl) {
        repo.invalidateUnused(user.getId(), type, Instant.now());
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repo.save(new EmailToken(user, type, RefreshTokenService.hash(raw), Instant.now().plus(ttl)));
        return raw;
    }

    /**
     * Redeems a link token (single use). One generic error for unknown, used, expired or wrong-kind tokens, so an
     * attacker learns nothing about which tokens exist.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public User consume(String rawToken, EmailToken.Type type) {
        Instant now = Instant.now();
        EmailToken token = repo.findByTokenHash(RefreshTokenService.hash(rawToken))
                .filter(t -> t.getType() == type && t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired."));
        token.markUsed(now);
        return (User) Hibernate.unproxy(token.getUser());
    }

    /** True if a token of this kind was issued to the user within the last {@code window}. */
    @Transactional(readOnly = true)
    public boolean issuedWithin(Long userId, EmailToken.Type type, Duration window) {
        return secondsUntilAllowed(userId, type, window) > 0;
    }

    /** Seconds until another token may be issued (0 if allowed now). */
    @Transactional(readOnly = true)
    public long secondsUntilAllowed(Long userId, EmailToken.Type type, Duration window) {
        return repo.findFirstByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .map(t -> Math.max(0, Duration.between(Instant.now(), t.getCreatedAt().plus(window)).toSeconds() + 1))
                .orElse(0L);
    }

    @Scheduled(cron = "${app.auth.email-token-purge-cron:0 29 3 * * *}")
    @Transactional
    public void purgeExpired() {
        repo.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }
}
