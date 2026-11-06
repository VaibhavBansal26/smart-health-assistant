package com.healthcare.app.auth;

import com.healthcare.app.user.Role;
import com.healthcare.app.user.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** 8-100 characters with at least one letter and one number (the same rule the Angular forms check). */
    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-100 characters and include a letter and a number";

    /** role is optional and only PATIENT is accepted: doctors are created by an administrator. */
    public record RegisterRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            Role role,
            String specialty) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, long expiresInSeconds, UserSummary user) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record MessageResponse(String message) {
    }
}
