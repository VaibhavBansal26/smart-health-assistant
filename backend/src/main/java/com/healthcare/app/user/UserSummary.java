package com.healthcare.app.user;

/** Safe public view of a user (never exposes the password hash). */
public record UserSummary(Long id, String fullName, String email, Role role, String specialty,
                          boolean emailVerified, boolean active) {
    public static UserSummary from(User u) {
        return new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getSpecialty(),
                u.isEmailVerified(), u.isActive());
    }
}
