package com.healthcare.app.auth;

import com.healthcare.app.exception.ApiException;
import com.healthcare.app.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Features that handle health information require a verified email address (REQUIRE_VERIFIED_EMAIL=true, the default). */
@Component
public class VerifiedEmailGuard {

    public static final String MESSAGE = "Please verify your email address first. We emailed you a link when you registered; "
            + "you can request a new one from the banner at the top of the page.";

    private final UserRepository users;
    private final boolean required;

    public VerifiedEmailGuard(UserRepository users, @Value("${app.auth.require-verified-email:true}") boolean required) {
        this.users = users;
        this.required = required;
    }

    public void require(Authentication auth) {
        if (!required) {
            return;
        }
        boolean verified = users.findByEmailIgnoreCase(auth.getName())
                .map(u -> u.isEmailVerified())
                .orElse(false);
        if (!verified) {
            throw new ApiException(HttpStatus.FORBIDDEN, MESSAGE);
        }
    }
}
