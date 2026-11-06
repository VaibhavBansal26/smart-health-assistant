package com.healthcare.app.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh-token cookie. Hardening choices:
 *  - HttpOnly: JavaScript (and therefore XSS) can never read it
 *  - Path=/api/auth: the browser only sends it to the login/refresh/logout endpoints, never with ordinary API calls
 *  - SameSite=Strict (configurable): not sent on cross-site requests, which is the main CSRF defence
 *  - Secure (configurable): only over HTTPS. Set COOKIE_SECURE=false only for local http development.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";

    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public RefreshCookies(@Value("${app.auth.cookie.secure:true}") boolean secure,
                          @Value("${app.auth.cookie.same-site:Strict}") String sameSite,
                          @Value("${app.auth.refresh-token-days:14}") long days) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = Duration.ofDays(days);
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(maxAge).build();
    }

    /** An expired cookie with the same name/path, which tells the browser to delete it. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite(sameSite).path(PATH);
    }
}
