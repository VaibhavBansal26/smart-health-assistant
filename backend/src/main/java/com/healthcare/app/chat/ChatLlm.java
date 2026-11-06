package com.healthcare.app.chat;

import java.util.List;
import java.util.function.Consumer;

/** One streaming chat backend. Implementations: Claude (real) and a scripted demo. */
public interface ChatLlm {

    record Turn(String role, String content) {
    }

    enum Outcome {
        /** Finished normally. */
        COMPLETE,
        /** The model's safety layer declined to answer. */
        REFUSED,
        /** Hit the output limit before finishing. */
        TRUNCATED
    }

    /** Thrown with a message that is safe to show to the end user. */
    class ChatLlmException extends RuntimeException {
        public ChatLlmException(String userMessage, Throwable cause) {
            super(userMessage, cause);
        }
    }

    /** Streams the assistant reply; each text chunk is passed to onText as it arrives. */
    Outcome stream(String systemPrompt, List<Turn> turns, Consumer<String> onText);

    /** "claude" or "demo", shown in the UI so users know what they are talking to. */
    String provider();

    String model();
}
