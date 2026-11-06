package com.healthcare.app.auth;

import com.healthcare.app.exception.ApiException;
import com.healthcare.app.mail.AccountMailer;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Email verification, password reset and doctor invitations. */
@Service
public class AccountService {

    private final UserRepository users;
    private final EmailTokenService tokens;
    private final AccountMailer mailer;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder encoder;
    private final Duration verifyTtl;
    private final Duration resetTtl;
    private final Duration inviteTtl;
    private final Duration cooldown;

    public AccountService(UserRepository users, EmailTokenService tokens, AccountMailer mailer,
                          RefreshTokenService refreshTokens, PasswordEncoder encoder,
                          @Value("${app.auth.verify-token-hours:24}") long verifyHours,
                          @Value("${app.auth.reset-token-minutes:60}") long resetMinutes,
                          @Value("${app.auth.invite-token-hours:72}") long inviteHours,
                          @Value("${app.auth.email-cooldown-seconds:60}") long cooldownSeconds) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.verifyTtl = Duration.ofHours(verifyHours);
        this.resetTtl = Duration.ofMinutes(resetMinutes);
        this.inviteTtl = Duration.ofHours(inviteHours);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /** Mails a verification link (used right after registration). */
    public void sendVerification(User user) {
        mailer.sendVerification(user, tokens.issue(user, EmailToken.Type.VERIFY_EMAIL, verifyTtl), verifyTtl);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        User user = tokens.consume(rawToken, EmailToken.Type.VERIFY_EMAIL);
        users.findById(user.getId()).ifPresent(u -> u.setEmailVerified(true));
    }

    /** The signed-in user asks for another link. Throttled so it cannot be used to spam an inbox. */
    public void resendVerification(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        long wait = tokens.secondsUntilAllowed(user.getId(), EmailToken.Type.VERIFY_EMAIL, cooldown);
        if (wait > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "A link was just sent. Please wait a moment before asking for another.", wait);
        }
        sendVerification(user);
    }

    /**
     * Always returns quietly: whether or not the address belongs to an account, the caller sees the same thing, so the
     * endpoint cannot be used to find out who is registered. Only active accounts that were not mailed in the last
     * minute actually receive a link.
     */
    public void forgotPassword(String email) {
        users.findByEmailIgnoreCase(email.trim())
                .filter(User::isActive)
                .filter(u -> !tokens.issuedWithin(u.getId(), EmailToken.Type.PASSWORD_RESET, cooldown))
                .ifPresent(u -> mailer.sendPasswordReset(u, tokens.issue(u, EmailToken.Type.PASSWORD_RESET, resetTtl), resetTtl));
    }

    /**
     * Redeems a reset (or doctor-invite) link. Receiving the link proves control of the inbox, so the email is also
     * marked verified. Every existing session is revoked: whoever may have had the old password is signed out.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User redeemed = tokens.consume(rawToken, EmailToken.Type.PASSWORD_RESET);
        User user = users.findById(redeemed.getId()).orElseThrow();
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired.");
        }
        user.setPassword(encoder.encode(newPassword));
        user.setEmailVerified(true);
        refreshTokens.revokeAllFor(user.getId(), RefreshToken.CREDENTIALS_CHANGED);
    }

    /** Emails a doctor the link to choose their own password (admin created the account). */
    public void inviteDoctor(User doctor) {
        mailer.sendDoctorInvite(doctor, tokens.issue(doctor, EmailToken.Type.PASSWORD_RESET, inviteTtl), inviteTtl);
    }
}
