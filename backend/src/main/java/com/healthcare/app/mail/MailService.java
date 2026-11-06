package com.healthcare.app.mail;

/** Sends one plain-text email. Implementations: log (development) and SMTP (real delivery). */
public interface MailService {
    void send(String to, String subject, String body);
}
