package com.healthcare.app.mail;

import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Email delivery")
class MailServiceTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    private JavaMailSenderImpl sender(int port) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost("localhost");
        s.setPort(port);
        s.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        s.getJavaMailProperties().put("mail.smtp.timeout", "2000");
        return s;
    }

    @Test
    void smtp_reallyDeliversTheMessage_toAMailServer() throws Exception {
        new SmtpMailService(sender(smtp.getSmtp().getPort()), "no-reply@clinic.test")
                .send("maya@example.test", "Verify your email address", "Click: http://app.test/verify-email?token=abc");

        assertTrue(smtp.waitForIncomingEmail(5000, 1));
        MimeMessage received = smtp.getReceivedMessages()[0];
        assertEquals("Verify your email address", received.getSubject());
        assertEquals("maya@example.test", received.getAllRecipients()[0].toString());
        assertEquals("no-reply@clinic.test", received.getFrom()[0].toString());
        assertTrue(GreenMailUtil.getBody(received).contains("verify-email?token=abc"));
    }

    @Test
    void smtp_failuresAreSwallowed_soTheUserFlowNeverBreaks() {
        var unreachable = new SmtpMailService(sender(1), "no-reply@clinic.test"); // nothing listens on port 1
        assertDoesNotThrow(() -> unreachable.send("maya@example.test", "Subject", "Body"));
    }

    // ---- AccountMailer: wording and links ----

    private static class Capture implements MailService {
        final List<String[]> sent = new ArrayList<>();
        @Override public void send(String to, String subject, String body) { sent.add(new String[]{to, subject, body}); }
    }

    private final User maya = new User("Maya Patel", "maya@example.test", "x", Role.PATIENT, null);

    @Test
    void links_usetheBaseUrl_withoutDoubledSlashes() {
        Capture capture = new Capture();
        new AccountMailer(capture, "https://app.example.com/").sendVerification(maya, "TOKEN123", Duration.ofHours(24));

        String body = capture.sent.get(0)[2];
        assertTrue(body.contains("https://app.example.com/verify-email?token=TOKEN123"), body);
        assertFalse(body.contains("com//verify"));
        assertTrue(body.contains("Maya Patel") && body.contains("24 hour"));
        assertEquals("maya@example.test", capture.sent.get(0)[0]);
    }

    @Test
    void resetAndInviteEmails_pointAtTheResetPage_andExplainWhy() {
        Capture capture = new Capture();
        AccountMailer mailer = new AccountMailer(capture, "http://app.test");
        User doctor = new User("Dr. Lee", "lee@clinic.test", "x", Role.DOCTOR, "Cardiology");

        mailer.sendPasswordReset(maya, "R1", Duration.ofMinutes(60));
        mailer.sendDoctorInvite(doctor, "D1", Duration.ofHours(72));

        assertTrue(capture.sent.get(0)[2].contains("http://app.test/reset-password?token=R1"));
        assertTrue(capture.sent.get(0)[2].contains("ignore this email"), "tells a non-requester nothing changed");
        assertTrue(capture.sent.get(1)[2].contains("http://app.test/reset-password?token=D1"));
        assertTrue(capture.sent.get(1)[2].contains("Cardiology") && capture.sent.get(1)[2].contains("72 hour"));
    }

    @Test
    void aMailerFailure_neverPropagates() {
        MailService broken = (to, subject, body) -> { throw new IllegalStateException("smtp down"); };
        AccountMailer mailer = new AccountMailer(broken, "http://app.test");
        assertDoesNotThrow(() -> mailer.sendVerification(maya, "T", Duration.ofHours(1)));
        assertDoesNotThrow(() -> mailer.sendPasswordReset(maya, "T", Duration.ofHours(1)));
    }
}
