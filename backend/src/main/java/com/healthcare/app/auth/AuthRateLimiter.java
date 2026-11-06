package com.healthcare.app.auth;

import com.healthcare.app.exception.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for the authentication endpoints (in memory: one server; use Redis or a gateway to share
 * state across several).
 *
 * Three independent guards:
 *  1. Per client IP: at most N requests to sensitive auth endpoints per window (stops scripted floods and mass sign-ups).
 *  2. Per (account, IP) pair: M failed logins lock THAT pair for a while. Keying on the pair means an attacker
 *     typing wrong passwords for a victim's email locks only themselves, not the real owner.
 *  3. Per account across all IPs: a higher ceiling that still stops a distributed (botnet) guessing campaign.
 * A correct login clears the pair's counter. Accounts that do not exist are counted identically, so the
 * response never reveals whether an email is registered.
 */
@Component
public class AuthRateLimiter {

    private static final int PURGE_ABOVE = 10_000;

    private record Failures(int count, long windowStart, long lockedUntil) {
    }

    private final int maxFailuresPerPair;
    private final int maxFailuresPerAccount;
    private final long lockoutMs;
    private final int ipMaxRequests;
    private final long ipWindowMs;
    private final Clock clock;

    private final ConcurrentHashMap<String, Failures> pairs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Failures> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> ips = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimiter(@Value("${app.auth.rate-limit.max-failed-logins:5}") int maxFailuresPerPair,
                           @Value("${app.auth.rate-limit.max-failed-logins-per-account:20}") int maxFailuresPerAccount,
                           @Value("${app.auth.rate-limit.lockout-minutes:15}") int lockoutMinutes,
                           @Value("${app.auth.rate-limit.ip-max-requests:60}") int ipMaxRequests,
                           @Value("${app.auth.rate-limit.ip-window-minutes:15}") int ipWindowMinutes) {
        this(maxFailuresPerPair, maxFailuresPerAccount, Duration.ofMinutes(lockoutMinutes), ipMaxRequests,
                Duration.ofMinutes(ipWindowMinutes), Clock.systemUTC());
    }

    AuthRateLimiter(int maxFailuresPerPair, int maxFailuresPerAccount, Duration lockout, int ipMaxRequests,
                    Duration ipWindow, Clock clock) {
        this.maxFailuresPerPair = maxFailuresPerPair;
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.lockoutMs = lockout.toMillis();
        this.ipMaxRequests = ipMaxRequests;
        this.ipWindowMs = ipWindow.toMillis();
        this.clock = clock;
    }

    /** Counts one request from this IP; throws 429 once the IP has used up its budget for the window. */
    public void checkIp(String ip) {
        long now = clock.millis();
        Deque<Long> q = ips.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() <= now - ipWindowMs) {
                q.pollFirst();
            }
            if (q.size() >= ipMaxRequests) {
                throw tooMany("Too many requests from your network. Please try again later.",
                        (q.peekFirst() + ipWindowMs - now));
            }
            q.addLast(now);
        }
        purgeIfLarge(now);
    }

    /** Throws 429 if this account/IP pair, or the account as a whole, is currently locked. */
    public void checkAccount(String email, String ip) {
        long now = clock.millis();
        for (Failures f : new Failures[]{pairs.get(pairKey(email, ip)), accounts.get(accountKey(email))}) {
            if (f != null && f.lockedUntil() > now) {
                throw tooMany("Too many failed sign-in attempts. Please try again later.", f.lockedUntil() - now);
            }
        }
    }

    public void recordFailure(String email, String ip) {
        long now = clock.millis();
        pairs.merge(pairKey(email, ip), new Failures(1, now, 0), (old, fresh) -> bump(old, now, maxFailuresPerPair));
        accounts.merge(accountKey(email), new Failures(1, now, 0), (old, fresh) -> bump(old, now, maxFailuresPerAccount));
        // a ceiling of 1 locks on the very first failure
        lockIfReached(pairs, pairKey(email, ip), maxFailuresPerPair, now);
        lockIfReached(accounts, accountKey(email), maxFailuresPerAccount, now);
    }

    /** A correct login clears the pair's counter (the account-wide counter keeps decaying on its own). */
    public void recordSuccess(String email, String ip) {
        pairs.remove(pairKey(email, ip));
    }

    private Failures bump(Failures old, long now, int max) {
        boolean windowExpired = now - old.windowStart() > lockoutMs && old.lockedUntil() <= now;
        if (windowExpired) {
            return new Failures(1, now, 0);
        }
        int count = old.count() + 1;
        return new Failures(count, old.windowStart(), count >= max ? now + lockoutMs : old.lockedUntil());
    }

    private void lockIfReached(ConcurrentHashMap<String, Failures> map, String key, int max, long now) {
        map.computeIfPresent(key, (k, f) -> f.count() >= max && f.lockedUntil() <= now
                ? new Failures(f.count(), f.windowStart(), now + lockoutMs) : f);
    }

    private ApiException tooMany(String message, long retryAfterMs) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message, Math.max(1, (retryAfterMs + 999) / 1000));
    }

    private void purgeIfLarge(long now) {
        if (pairs.size() + accounts.size() + ips.size() < PURGE_ABOVE) {
            return;
        }
        pairs.values().removeIf(f -> now - f.windowStart() > lockoutMs && f.lockedUntil() <= now);
        accounts.values().removeIf(f -> now - f.windowStart() > lockoutMs && f.lockedUntil() <= now);
        ips.values().removeIf(q -> q.isEmpty() || q.peekLast() <= now - ipWindowMs);
    }

    private static String accountKey(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String pairKey(String email, String ip) {
        return accountKey(email) + "|" + ip;
    }
}
