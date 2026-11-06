package com.healthcare.app.admin;

import com.healthcare.app.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record CreateDoctorRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String specialty) {
    }

    /** PATCH: send only what changes. */
    public record UpdateDoctorRequest(
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "Name cannot be blank") String fullName,
            @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "Specialty cannot be blank") String specialty,
            Boolean active) {
    }

    /** setupPending = the doctor has not yet chosen a password through the invitation link. */
    public record AdminDoctor(Long id, String fullName, String email, String specialty, boolean active,
                              boolean setupPending) {
        public static AdminDoctor from(User u) {
            return new AdminDoctor(u.getId(), u.getFullName(), u.getEmail(), u.getSpecialty(), u.isActive(),
                    !u.isEmailVerified());
        }
    }
}
