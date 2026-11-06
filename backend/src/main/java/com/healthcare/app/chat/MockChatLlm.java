package com.healthcare.app.chat;

import com.healthcare.app.recommendation.MlClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scripted "demo mode" used when no Anthropic credentials are configured, so the
 * whole chat flow (streaming, safety, UI) can be developed and demoed offline.
 * It asks generic follow-up questions and, when the ML service is up, hints at a specialty.
 */
public class MockChatLlm implements ChatLlm {

    private final MlClient ml;

    public MockChatLlm(MlClient ml) {
        this.ml = ml;
    }

    @Override
    public Outcome stream(String systemPrompt, List<Turn> turns, Consumer<String> onText) {
        String last = turns.get(turns.size() - 1).content();

        String hint = "";
        try {
            var top = ml.predict(last).predictions().get(0);
            if (top.confidence() >= 0.35) {
                hint = "From what you describe, a " + top.specialty() + " specialist may be a good fit. ";
            }
        } catch (RuntimeException ignored) {
            // ML service down or unusable answer: the demo reply simply has no specialty hint
        }

        String reply = "Thanks for telling me. " + hint
                + "To understand better, could you share how long this has been going on, "
                + "how strong it is on a scale of 1 to 10, and whether anything makes it better or worse?\n\n"
                + "If it suddenly gets worse, or you feel unsafe, please seek urgent care.\n\n"
                + "(Demo mode: no AI key is configured, so this is a scripted reply.)";

        for (String word : reply.split("(?<= )")) { // keep the trailing space with each word
            onText.accept(word);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Outcome.COMPLETE;
    }

    @Override
    public String provider() {
        return "demo";
    }

    @Override
    public String model() {
        return "scripted-demo";
    }
}
