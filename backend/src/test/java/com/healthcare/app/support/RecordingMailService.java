package com.healthcare.app.support;

import com.healthcare.app.mail.MailService;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test mail service: remembers every email so tests can read the links exactly as a user would. */
@Component
@Primary
@Profile("test")
public class RecordingMailService implements MailService {

    public record Mail(String to, String subject, String body) {
    }

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Mail(to, subject, body));
    }

    public List<Mail> to(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    public int countTo(String email) {
        return to(email).size();
    }

    public Mail latestTo(String email, String subjectContains) {
        List<Mail> matching = to(email).stream().filter(m -> m.subject().contains(subjectContains)).toList();
        if (matching.isEmpty()) {
            throw new AssertionError("No email to " + email + " with subject containing '" + subjectContains + "'. Sent: " + to(email));
        }
        return matching.get(matching.size() - 1);
    }

    /** The token in the email's link. */
    public String tokenIn(Mail mail) {
        Matcher m = TOKEN.matcher(mail.body());
        if (!m.find()) {
            throw new AssertionError("No token link in: " + mail.body());
        }
        return m.group(1);
    }
}
