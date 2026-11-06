package com.healthcare.app.mail;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development mail: "sends" by writing the whole email, links included, to the application log, so you can click
 * verification and reset links without an SMTP server. NEVER use in production: the log would contain working
 * account-takeover links. Set MAIL_MODE=smtp there.
 */
@Service
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
public class LoggingMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);

    @PostConstruct
    void warn() {
        log.warn("MAIL_MODE=log: emails are written to this log, links included. Development only; use MAIL_MODE=smtp in production.");
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("\n---------- EMAIL (log mode) ----------\nTo: {}\nSubject: {}\n\n{}\n--------------------------------------", to, subject, body);
    }
}
