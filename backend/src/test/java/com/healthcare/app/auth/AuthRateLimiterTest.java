package com.healthcare.app.auth;

import com.healthcare.app.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Auth rate limiter")
class AuthRateLimiterTest {

    /** A clock the test moves by hand, so lockouts expire without sleeping. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MutableClock clock;
    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        // 3 failures per (account, IP), 10 per account overall, 15 min lockout, 5 requests per IP per 15 min
        limiter = new AuthRateLimiter(3, 10, Duration.ofMinutes(15), 5, Duration.ofMinutes(15), clock);
    }

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAccount(email, ip);
            limiter.recordFailure(email, ip);
        }
    }

    private ApiException lockedOut(String email, String ip) {
        return assertThrows(ApiException.class, () -> limiter.checkAccount(email, ip));
    }

    @Test
    void locksThePair_afterTheMaxFailures_with429AndRetryAfter() {
        fail("a@x.test", "1.1.1.1", 3);
        ApiException e = lockedOut("a@x.test", "1.1.1.1");
        assertEquals(429, e.getStatus().value());
        assertEquals(15 * 60, e.getRetryAfterSeconds());
    }

    @Test
    void doesNotLock_beforeTheLimit() {
        fail("a@x.test", "1.1.1.1", 2);
        assertDoesNotThrow(() -> limiter.checkAccount("a@x.test", "1.1.1.1"));
    }

    @Test
    void aCorrectLoginResetsTheCounter() {
        fail("a@x.test", "1.1.1.1", 2);
        limiter.recordSuccess("a@x.test", "1.1.1.1");
        fail("a@x.test", "1.1.1.1", 2);
        assertDoesNotThrow(() -> limiter.checkAccount("a@x.test", "1.1.1.1"));
    }

    @Test
    void theLockoutExpires_andCountingStartsFresh() {
        fail("a@x.test", "1.1.1.1", 3);
        clock.advance(Duration.ofMinutes(14));
        assertEquals(60, lockedOut("a@x.test", "1.1.1.1").getRetryAfterSeconds());

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertDoesNotThrow(() -> limiter.checkAccount("a@x.test", "1.1.1.1"));
        fail("a@x.test", "1.1.1.1", 2); // would be failure 5 if the old count survived
        assertDoesNotThrow(() -> limiter.checkAccount("a@x.test", "1.1.1.1"));
    }

    @Test
    void anAttackerGuessingAVictimsPassword_cannotLockTheRealOwnerOut() {
        fail("victim@x.test", "6.6.6.6", 3); // attacker's IP is now locked for this account
        lockedOut("victim@x.test", "6.6.6.6");
        assertDoesNotThrow(() -> limiter.checkAccount("victim@x.test", "2.2.2.2")); // owner, elsewhere
    }

    @Test
    void aDistributedCampaign_isStoppedByTheAccountWideCeiling() {
        fail("victim@x.test", "1.0.0.1", 3);
        fail("victim@x.test", "1.0.0.2", 3);
        fail("victim@x.test", "1.0.0.3", 3);
        fail("victim@x.test", "1.0.0.4", 1); // 10th failure overall
        lockedOut("victim@x.test", "1.0.0.99"); // an IP that never tried anything is refused too
    }

    @Test
    void emailsAreMatchedCaseInsensitively_andOtherAccountsAreIndependent() {
        limiter.recordFailure("Mixed@X.test", "1.1.1.1");
        limiter.recordFailure("mixed@x.TEST ", "1.1.1.1");
        limiter.recordFailure("MIXED@x.test", "1.1.1.1");
        lockedOut("mixed@x.test", "1.1.1.1");
        assertDoesNotThrow(() -> limiter.checkAccount("someone-else@x.test", "1.1.1.1"));
    }

    @Test
    void anIpMayOnlyMakeSo_manyRequestsPerWindow() {
        for (int i = 0; i < 5; i++) limiter.checkIp("9.9.9.9");
        ApiException e = assertThrows(ApiException.class, () -> limiter.checkIp("9.9.9.9"));
        assertEquals(429, e.getStatus().value());
        assertTrue(e.getRetryAfterSeconds() > 0 && e.getRetryAfterSeconds() <= 900);
        assertDoesNotThrow(() -> limiter.checkIp("8.8.8.8")); // other IPs unaffected

        clock.advance(Duration.ofMinutes(16));
        assertDoesNotThrow(() -> limiter.checkIp("9.9.9.9"));
    }
}
