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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Register, log in and identify the current user")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final AuthRateLimiter limiter;

    public AuthController(UserRepository users, PasswordEncoder encoder,
                          AuthenticationManager authManager, JwtService jwt, AuthRateLimiter limiter) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    @SecurityRequirements // public
    @Operation(summary = "Create an account (patient or doctor)", description = "Returns a JWT so the user is logged in immediately. 409 if the email is taken.")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
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
        return toResponse(saved);
    }

    @PostMapping("/login")
    @SecurityRequirements // public
    @Operation(summary = "Log in", description = "Returns a JWT to send as `Authorization: Bearer <token>`. 401 for wrong credentials; "
            + "429 (with Retry-After) after repeated failures.")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
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
        return toResponse(user);
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user")
    public UserSummary me(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName())
                .map(UserSummary::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    private AuthResponse toResponse(User user) {
        String token = jwt.generate(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, jwt.ttlSeconds(), UserSummary.from(user));
    }
}
