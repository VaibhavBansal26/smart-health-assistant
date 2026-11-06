package com.healthcare.app.auth;

import com.healthcare.app.auth.AuthDtos.ForgotPasswordRequest;
import com.healthcare.app.auth.AuthDtos.MessageResponse;
import com.healthcare.app.auth.AuthDtos.ResetPasswordRequest;
import com.healthcare.app.auth.AuthDtos.VerifyEmailRequest;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Account", description = "Email verification and password reset")
public class AccountController {

    static final String FORGOT_MESSAGE = "If an account exists for that email, we have sent a link to reset the password.";

    private final AccountService accounts;
    private final AuthRateLimiter limiter;
    private final UserRepository users;

    public AccountController(AccountService accounts, AuthRateLimiter limiter, UserRepository users) {
        this.accounts = accounts;
        this.limiter = limiter;
        this.users = users;
    }

    @PostMapping("/verify-email")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm an email address with the token from the verification email",
            description = "Single use. 400 if the token is unknown, used or expired.")
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest req, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        accounts.verifyEmail(req.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Send a new verification email to the signed-in user",
            description = "No-op if already verified. 429 (with Retry-After) if one was sent in the last minute.")
    public void resendVerification(Authentication auth, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        accounts.resendVerification(users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists")));
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Email a password reset link",
            description = "Always answers 202 with the same message, whether or not the email belongs to an account "
                    + "(so it cannot be used to discover who is registered).")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        accounts.forgotPassword(req.email());
        return new MessageResponse(FORGOT_MESSAGE);
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Choose a new password with the token from the reset (or doctor invitation) email",
            description = "Single use. Signs the account out everywhere and marks the email verified. 400 for a bad link or weak password.")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        limiter.checkIp(http.getRemoteAddr());
        accounts.resetPassword(req.token(), req.newPassword());
    }
}
