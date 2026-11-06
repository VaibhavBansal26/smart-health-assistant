package com.healthcare.app.auth;

import com.healthcare.app.auth.AuthDtos.AuthResponse;
import com.healthcare.app.auth.AuthDtos.LoginRequest;
import com.healthcare.app.auth.AuthDtos.RegisterRequest;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.security.JwtService;
import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import com.healthcare.app.user.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Register, log in, refresh the session and identify the current user")
public class AuthController {

    /** Cross-site HTML forms cannot send custom headers, so requiring one is cheap CSRF defence for cookie endpoints. */
    private static final String XHR_HEADER = "X-Requested-With";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final AuthRateLimiter limiter;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;

    public AuthController(UserRepository users, PasswordEncoder encoder, AuthenticationManager authManager,
                          JwtService jwt, AuthRateLimiter limiter, RefreshTokenService refreshTokens,
                          RefreshCookies cookies) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.limiter = limiter;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @SecurityRequirements // public
    @Operation(summary = "Create an account (patient or doctor)",
            description = "Returns a short-lived access token in the body and sets the refresh token as an HttpOnly cookie. "
                    + "409 if the email is taken.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        if (req.role() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot self-register as ADMIN");
        }
        if (req.role() == Role.DOCTOR && (req.specialty() == null || req.specialty().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Specialty is required for doctors");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User saved = users.save(new User(
                req.fullName().trim(),
                req.email().trim().toLowerCase(),
                encoder.encode(req.password()),
                req.role(),
                req.role() == Role.DOCTOR ? req.specialty().trim() : null));
        return session(saved, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @SecurityRequirements // public
    @Operation(summary = "Log in",
            description = "Returns a short-lived access token (send as `Authorization: Bearer <token>`) and sets the refresh "
                    + "token as an HttpOnly cookie. 401 for wrong credentials; 429 (with Retry-After) after repeated failures.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        limiter.checkIp(ip);
        limiter.checkAccount(req.email(), ip);
        try {
            // Throws BadCredentialsException on failure -> mapped to 401 by GlobalExceptionHandler
            authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (AuthenticationException e) {
            limiter.recordFailure(req.email(), ip);
            throw e;
        }
        limiter.recordSuccess(req.email(), ip);
        User user = users.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return session(user, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @SecurityRequirements // authenticated by the cookie, not by a bearer token
    @Operation(summary = "Get a new access token using the refresh cookie",
            description = "Rotates the refresh token (the old one stops working). Needs the `X-Requested-With` header "
                    + "and the cookie set at login. 401 if the session is missing, expired or revoked.")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = XHR_HEADER, required = false) String xhr,
            HttpServletResponse response) {
        requireXhr(xhr);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        try {
            RefreshTokenService.Issued next = refreshTokens.rotate(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookies.create(next.rawToken()).toString())
                    .body(toResponse(next.user()));
        } catch (ApiException invalid) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString()); // stop the browser resending a dead cookie
            throw invalid;
        }
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Log out: revokes the refresh token and clears the cookie",
            description = "The short-lived access token already issued stays valid until it expires (minutes).")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            @RequestHeader(name = XHR_HEADER, required = false) String xhr) {
        requireXhr(xhr);
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revoke(refreshToken);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user")
    public UserSummary me(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName())
                .map(UserSummary::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private ResponseEntity<AuthResponse> session(User user, HttpStatus status) {
        RefreshTokenService.Issued issued = refreshTokens.issue(user);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(issued.rawToken()).toString())
                .body(toResponse(user));
    }

    private AuthResponse toResponse(User user) {
        String token = jwt.generate(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, jwt.ttlSeconds(), UserSummary.from(user));
    }

    private void requireXhr(String header) {
        if (header == null || header.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Missing " + XHR_HEADER + " header");
        }
    }
}
