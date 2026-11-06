package com.healthcare.app.admin;

import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD (there is no sign-up form for admins, on purpose).
 * Only creates; it never overwrites an existing account, so changing ADMIN_PASSWORD later does NOT reset the password
 * (use "forgot password" for that). After the first start you can remove ADMIN_PASSWORD from the environment.
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    @Bean
    CommandLineRunner bootstrapAdmin(UserRepository users, PasswordEncoder encoder,
                                     @Value("${app.admin.email:}") String email,
                                     @Value("${app.admin.password:}") String password,
                                     @Value("${app.admin.full-name:Administrator}") String fullName) {
        return args -> {
            if (email.isBlank()) {
                return;
            }
            if (users.existsByEmailIgnoreCase(email.trim())) {
                return;
            }
            if (password.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalStateException("ADMIN_EMAIL is set but ADMIN_PASSWORD is missing or shorter than "
                        + MIN_PASSWORD_LENGTH + " characters. See .env.example.");
            }
            users.save(new User(fullName.trim(), email.trim().toLowerCase(), encoder.encode(password), Role.ADMIN, null));
            log.info("Created the initial administrator account");
        };
    }
}
