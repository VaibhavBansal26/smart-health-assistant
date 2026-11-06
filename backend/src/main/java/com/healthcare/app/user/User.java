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
}
