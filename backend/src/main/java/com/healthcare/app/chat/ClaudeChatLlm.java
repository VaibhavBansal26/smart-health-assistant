package com.healthcare.app.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Real implementation using the official Anthropic Java SDK.
 * Message content is never logged: it is health data.
 */
public class ClaudeChatLlm implements ChatLlm {

    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort effort; // null = model default
    private final long maxTokens;

    /**
     * Credentials: an explicit key/token from Spring config (so a .env file works), otherwise whatever the SDK
     * finds in the process environment or an `ant auth login` profile.
     */
    public ClaudeChatLlm(String model, String effort, long maxTokens, String apiKey, String authToken) {
        var builder = AnthropicOkHttpClient.builder().fromEnv();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.apiKey(apiKey);
        } else if (authToken != null && !authToken.isBlank()) {
            builder.authToken(authToken);
        }
        this.client = builder.build();
        this.model = model;
        this.effort = effort == null || effort.isBlank() ? null : OutputConfig.Effort.of(effort.trim().toLowerCase());
        this.maxTokens = maxTokens;
    }

    @Override
    public Outcome stream(String systemPrompt, List<Turn> turns, Consumer<String> onText) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model) // String overload: works for any model id
                .maxTokens(maxTokens)
                .system(systemPrompt);
        for (Turn t : turns) {
            if (t.role().equals("assistant")) {
                params.addAssistantMessage(t.content());
            } else {
                params.addUserMessage(t.content());
            }
        }
        if (effort != null) {
            params.outputConfig(OutputConfig.builder().effort(effort).build());
        }

        AtomicReference<String> stopReason = new AtomicReference<>("");
        try (StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params.build())) {
            response.stream().forEach(event -> {
                // Only visible text is forwarded; any thinking blocks stay server-side.
                event.contentBlockDelta()
                        .flatMap(d -> d.delta().text())
                        .ifPresent(t -> onText.accept(t.text()));
                event.messageDelta()
                        .flatMap(m -> m.delta().stopReason())
                        .ifPresent(r -> stopReason.set(r.asString()));
            });
        } catch (RateLimitException e) {
            throw new ChatLlmException("The assistant is very busy right now. Please try again in a minute.", e);
        } catch (AnthropicException e) {
            throw new ChatLlmException("The assistant could not respond right now. Please try again.", e);
        }

        return switch (stopReason.get()) {
            case "refusal" -> Outcome.REFUSED;
            case "max_tokens" -> Outcome.TRUNCATED;
            default -> Outcome.COMPLETE;
        };
    }

    @Override
    public String provider() {
        return "claude";
    }

    @Override
    public String model() {
        return model;
    }
}
