package com.healthcare.app.user;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Only set for doctors. */
    private String specialty;

    /**
     * Whether the person has proven they control the email address. Accounts created by trusted paths (seed data, the
     * admin bootstrap) start verified; self-registration sets this to false until the emailed link is used.
     */
    @Column(nullable = false)
    private boolean emailVerified = true;

    /** Deactivated accounts cannot sign in, keep no sessions, and (for doctors) are hidden and cannot be booked. */
    @Column(nullable = false)
    private boolean active = true;

    protected User() {
    }

    public User(String fullName, String email, String password, Role role, String specialty) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
        this.specialty = specialty;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public Role getRole() { return role; }
    public String getSpecialty() { return specialty; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isActive() { return active; }

    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setActive(boolean active) { this.active = active; }
    public void setPassword(String passwordHash) { this.password = passwordHash; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
}
