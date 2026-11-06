package com.healthcare.app.mail;

import com.healthcare.app.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** The wording of the account emails. Links point at the Angular app (APP_BASE_URL). */
@Component
public class AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(AccountMailer.class);

    private final MailService mail;
    private final String baseUrl;

    public AccountMailer(MailService mail, @Value("${app.base-url}") String baseUrl) {
        this.mail = mail;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void sendVerification(User user, String token, Duration validFor) {
        deliver(user, "Verify your email address",
                "Hello " + user.getFullName() + ",\n\n"
                        + "Please confirm your email address to finish setting up your Smart Health Assistant account:\n\n"
                        + baseUrl + "/verify-email?token=" + token + "\n\n"
                        + "This link works once and expires in " + describe(validFor) + ".\n"
                        + "If you did not create an account, you can ignore this email.\n");
    }

    public void sendPasswordReset(User user, String token, Duration validFor) {
        deliver(user, "Reset your password",
                "Hello " + user.getFullName() + ",\n\n"
                        + "We received a request to reset your password. Choose a new one here:\n\n"
                        + baseUrl + "/reset-password?token=" + token + "\n\n"
                        + "This link works once and expires in " + describe(validFor) + ".\n"
                        + "If you did not ask for this, ignore this email: your password has not changed.\n");
    }

    public void sendDoctorInvite(User doctor, String token, Duration validFor) {
        deliver(doctor, "Set up your doctor account",
                "Hello " + doctor.getFullName() + ",\n\n"
                        + "An administrator created a doctor account for you on Smart Health Assistant "
                        + "(specialty: " + doctor.getSpecialty() + "). Choose your password to activate it:\n\n"
                        + baseUrl + "/reset-password?token=" + token + "\n\n"
                        + "This link works once and expires in " + describe(validFor) + ".\n");
    }

    private void deliver(User user, String subject, String body) {
        try {
            mail.send(user.getEmail(), subject, body);
        } catch (RuntimeException e) {
            // never let a mail problem break registration or reveal (via an error) whether an account exists
            log.error("Could not hand an email to the mail service ({})", e.getClass().getSimpleName());
        }
    }

    private static String describe(Duration d) {
        return d.toHours() >= 1 && d.toMinutes() % 60 == 0 ? d.toHours() + " hour(s)" : d.toMinutes() + " minutes";
    }
}
