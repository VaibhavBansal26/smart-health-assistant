package com.healthcare.app.recommendation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic emergency rules that run BEFORE the ML model.
 * Safety must never depend on a statistical model, so these are plain,
 * reviewable patterns. They deliberately err on the side of flagging.
 */
@Component
public class RedFlagDetector {

    public record RedFlag(String category, String message) {
    }

    private record Rule(String category, Pattern primary, Pattern alsoRequired, String message) {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static final String CALL = "Please call your local emergency number or go to the nearest emergency department now. ";

    private static final List<Rule> RULES = List.of(
            new Rule("Possible heart attack",
                    // the many ways people say "chest pain": "chest pain", "pain in my chest", "my chest hurts", "tightness across the chest"
                    p("chest\\s+(pain|pressure|tightness|heaviness|discomfort|ache)"
                            + "|chest (hurts|is hurting|aches|feels (tight|heavy|crushed))"
                            + "|(pain|ache|pressure|tightness|heaviness|discomfort|squeezing( feeling)?) (in|across|on) (my |the )?chest"),
                    // accompanying symptoms of a heart attack, matched by word stem so "nauseous", "dizzy", "sweaty" all count
                    p("short(ness)? of breath|breathless|left arm|\\bjaw\\b|sweat|nause|dizz|light-?headed|faint|passing out|passed out"),
                    "Chest pain together with these symptoms can be a heart attack. " + CALL + "Do not drive yourself."),
            new Rule("Possible stroke",
                    p("slurred speech|face (is )?droop|drooping face|sudden(ly)? (weakness|numbness|confusion|loss of vision)|can'?t (speak|move (my )?(arm|leg))|worst headache of my life"),
                    null,
                    "These can be signs of a stroke, where minutes matter. " + CALL),
            new Rule("Thoughts of self-harm",
                    p("suicid|kill myself|end my life|want to die|self[- ]?harm|hurt myself|no reason to live"),
                    null,
                    "You are not alone, and help is available right now. Please contact your local emergency number or a crisis helpline, "
                            + "or reach out to someone you trust and ask them to stay with you."),
            new Rule("Severe breathing difficulty",
                    p("can'?t breathe|cannot breathe|unable to breathe|gasping for air|struggling to breathe|lips (are )?turning blue"),
                    null,
                    "Severe difficulty breathing is an emergency. " + CALL),
            new Rule("Severe bleeding",
                    p("vomiting blood|coughing (up )?blood|blood in (my )?vomit|heavy bleeding|severe bleeding|bleeding (heavily|that won'?t stop|won'?t stop)"),
                    null,
                    "Heavy or unexplained bleeding needs urgent care. " + CALL),
            new Rule("Loss of consciousness",
                    p("unconscious|unresponsive|not waking up|having a seizure"),
                    null,
                    "This may be a medical emergency. " + CALL),
            new Rule("Possible severe allergic reaction",
                    p("(face|lips|tongue|throat) (is |are )?swell(ing|ed|s)?|swollen (face|lips|tongue|throat)"),
                    p("breath|swallow"),
                    "Swelling with trouble breathing or swallowing can be a severe allergic reaction. " + CALL)
    );

    public Optional<RedFlag> detect(String text) {
        for (Rule r : RULES) {
            if (r.primary().matcher(text).find()
                    && (r.alsoRequired() == null || r.alsoRequired().matcher(text).find())) {
                return Optional.of(new RedFlag(r.category(), r.message()));
            }
        }
        return Optional.empty();
    }
}
