package com.healthcare.app.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JWT service")
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";

    @Test
    void aTokenItIssued_validatesAndYieldsTheSubject() {
        var jwt = new JwtService(SECRET, 60);
        assertEquals("a@b.test", jwt.validateAndGetSubject(jwt.generate("a@b.test", "PATIENT")).orElseThrow());
    }

    @Test
    void anExpiredToken_isRejected() {
        var jwt = new JwtService(SECRET, 0); // expires immediately
        assertTrue(jwt.validateAndGetSubject(jwt.generate("a@b.test", "PATIENT")).isEmpty());
    }

    @Test
    void aTokenSignedWithAnotherSecret_isRejected() {
        var other = new JwtService("a-completely-different-secret-value-0987654321", 60);
        var mine = new JwtService(SECRET, 60);
        assertTrue(mine.validateAndGetSubject(other.generate("a@b.test", "PATIENT")).isEmpty());
    }

    @Test
    void aTamperedToken_isRejected() {
        var jwt = new JwtService(SECRET, 60);
        String token = jwt.generate("a@b.test", "PATIENT");
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("abc") ? "xyz" : "abc");
        assertTrue(jwt.validateAndGetSubject(tampered).isEmpty());
    }

    @Test
    void garbage_isRejected_notThrown() {
        var jwt = new JwtService(SECRET, 60);
        for (String junk : new String[]{"", "abc", "a.b.c", "Bearer x", "eyJhbGciOiJub25lIn0.e30."}) {
            assertTrue(jwt.validateAndGetSubject(junk).isEmpty(), "should reject: " + junk);
        }
    }

    @Test
    void reportsItsLifetime() {
        assertEquals(3600, new JwtService(SECRET, 60).ttlSeconds());
    }

    @Test
    void refusesToStart_withAMissingOrShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 60));
        assertThrows(IllegalStateException.class, () -> new JwtService("", 60));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 60));
        var e = assertThrows(IllegalStateException.class, () -> new JwtService("short", 60));
        assertTrue(e.getMessage().contains("JWT_SECRET"), "the message must name the variable to set");
    }
}
