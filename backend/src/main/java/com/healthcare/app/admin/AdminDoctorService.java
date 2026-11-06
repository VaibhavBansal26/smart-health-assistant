package com.healthcare.app.admin;

import com.healthcare.app.admin.AdminDtos.AdminDoctor;
import com.healthcare.app.admin.AdminDtos.CreateDoctorRequest;
import com.healthcare.app.admin.AdminDtos.UpdateDoctorRequest;
import com.healthcare.app.auth.AccountService;
import com.healthcare.app.auth.RefreshToken;
import com.healthcare.app.auth.RefreshTokenService;
import com.healthcare.app.common.PageResponse;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
public class AdminDoctorService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AccountService accounts;
    private final RefreshTokenService refreshTokens;
    private final SecureRandom random = new SecureRandom();

    public AdminDoctorService(UserRepository users, PasswordEncoder encoder, AccountService accounts,
                              RefreshTokenService refreshTokens) {
        this.users = users;
        this.encoder = encoder;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDoctor> list(String q, String specialty, Boolean active, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> cb.equal(root.get("role"), Role.DOCTOR);
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (specialty != null && !specialty.isBlank()) {
            String wanted = specialty.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("specialty")), wanted));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like, '\\'),
                    cb.like(cb.lower(root.get("email")), like, '\\'),
                    cb.like(cb.lower(root.get("specialty")), like, '\\')));
        }
        return PageResponse.of(users.findAll(spec, pageable), AdminDoctor::from);
    }

    /**
     * Creates the account with a random password nobody knows, then mails the doctor a link to choose their own.
     * Until they do, the account cannot be signed into ("setup pending").
     */
    @Transactional
    public AdminDoctor create(CreateDoctorRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        byte[] unusable = new byte[32];
        random.nextBytes(unusable);
        User doctor = new User(req.fullName().trim(), email,
                encoder.encode(Base64.getEncoder().encodeToString(unusable)), Role.DOCTOR, req.specialty().trim());
        doctor.setEmailVerified(false);
        User saved = users.save(doctor);
        accounts.inviteDoctor(saved);
        return AdminDoctor.from(saved);
    }

    @Transactional
    public AdminDoctor update(Long id, UpdateDoctorRequest req) {
        if (req.fullName() == null && req.specialty() == null && req.active() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide a name, a specialty, or an active flag");
        }
        User doctor = findDoctor(id);
        if (req.fullName() != null) doctor.setFullName(req.fullName().trim());
        if (req.specialty() != null) doctor.setSpecialty(req.specialty().trim());
        if (req.active() != null && req.active() != doctor.isActive()) {
            doctor.setActive(req.active());
            if (!req.active()) {
                // deactivation takes effect immediately: every session is revoked (old access tokens are refused too)
                refreshTokens.revokeAllFor(doctor.getId(), RefreshToken.CREDENTIALS_CHANGED);
            }
        }
        return AdminDoctor.from(doctor);
    }

    @Transactional
    public void resendInvite(Long id) {
        User doctor = findDoctor(id);
        if (doctor.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "This doctor has already set up their account");
        }
        accounts.inviteDoctor(doctor);
    }

    private User findDoctor(Long id) {
        return users.findById(id).filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
    }
}
