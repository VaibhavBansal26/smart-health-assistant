package com.healthcare.app.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.app.chat.ChatDtos.ChatRequest;
import com.healthcare.app.chat.ChatDtos.StatusResponse;
import com.healthcare.app.chat.ChatLlm.ChatLlmException;
import com.healthcare.app.chat.ChatLlm.Outcome;
import com.healthcare.app.chat.ChatLlm.Turn;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.recommendation.RedFlagDetector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Streaming chat over Server-Sent Events, written straight to the servlet response.
 * (SseEmitter/StreamingResponseBody use async dispatch, which re-runs Spring Security
 * without our JWT filter; writing on the request thread avoids that, and virtual
 * threads make the blocking cheap.)
 *
 * Events: delta {text} | emergency {message} | notice {message} | error {message} | done {}
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "AI chat", description = "Streaming health chat (Server-Sent Events)")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_TURNS = 20;
    /** Red flags are checked over the latest few user messages, since symptoms arrive piecemeal. */
    private static final int RED_FLAG_WINDOW = 3;

    private final ChatLlm llm;
    private final RedFlagDetector redFlags;
    private final ChatRateLimiter limiter;
    private final ObjectMapper json;

    public ChatController(ChatLlm llm, RedFlagDetector redFlags, ChatRateLimiter limiter, ObjectMapper json) {
        this.llm = llm;
        this.redFlags = redFlags;
        this.limiter = limiter;
        this.json = json;
    }

    @GetMapping("/status")
    @Operation(summary = "Which AI provider is active (claude or demo)")
    @PreAuthorize("hasRole('PATIENT')")
    public StatusResponse status() {
        return new StatusResponse(llm.provider(), llm.model());
    }

    @PostMapping(value = "/stream", produces = "text/event-stream")
    @Operation(summary = "Send the conversation, stream the reply (patients only)",
            description = "Response is text/event-stream with events: `delta {text}`, `emergency {message}`, `notice {message}`, "
                    + "`error {message}`, `done`. Failures before streaming starts (400, 401, 429) are normal JSON errors. "
                    + "Swagger UI cannot display a live stream; use curl -N.")
    @PreAuthorize("hasRole('PATIENT')")
    public void stream(@Valid @RequestBody ChatRequest req, Authentication auth, HttpServletResponse res)
            throws IOException {
        // Everything that can fail with a normal JSON error happens BEFORE the stream starts.
        if (!limiter.tryAcquire(auth.getName())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You are sending messages too quickly. Please wait a moment.");
        }
        List<Turn> turns = prepareTurns(req);

        res.setStatus(200);
        res.setContentType("text/event-stream");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-cache");
        res.setHeader("X-Accel-Buffering", "no"); // stop nginx-style proxies from buffering the stream
        OutputStream out = res.getOutputStream();

        try {
            // 1. Safety first: deterministic rules, the model is never consulted for emergencies.
            var flag = redFlags.detect(recentUserText(turns));
            if (flag.isPresent()) {
                send(out, "emergency", Map.of("message", flag.get().message()));
                send(out, "done", Map.of());
                return;
            }

            // 2. Stream the model's answer chunk by chunk.
            Outcome outcome = llm.stream(ChatPrompts.SYSTEM, turns, text -> {
                try {
                    send(out, "delta", Map.of("text", text));
                } catch (IOException e) {
                    throw new UncheckedIOException(e); // client went away: abort the upstream stream
                }
            });
            if (outcome == Outcome.REFUSED) {
                send(out, "notice", Map.of("message", ChatPrompts.REFUSAL_MESSAGE));
            } else if (outcome == Outcome.TRUNCATED) {
                send(out, "notice", Map.of("message", "That answer was cut short. Ask me to continue if you need more."));
            }
            send(out, "done", Map.of());
        } catch (UncheckedIOException disconnected) {
            // user closed the tab or pressed Stop: nothing to do
        } catch (ChatLlmException e) {
            log.warn("Chat model call failed: {}", e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName());
            send(out, "error", Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Unexpected chat failure: {}", e.getClass().getSimpleName());
            send(out, "error", Map.of("message", "Something went wrong. Please try again."));
        }
    }

    private List<Turn> prepareTurns(ChatRequest req) {
        List<Turn> turns = new ArrayList<>();
        req.messages().forEach(m -> turns.add(new Turn(m.role(), m.content().trim())));
        List<Turn> recent = new ArrayList<>(turns.subList(Math.max(0, turns.size() - MAX_TURNS), turns.size()));
        while (!recent.isEmpty() && recent.get(0).role().equals("assistant")) {
            recent.remove(0); // the API wants the conversation to start with the user
        }
        if (recent.isEmpty() || !recent.get(recent.size() - 1).role().equals("user")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The last message must come from the user.");
        }
        return recent;
    }

    private String recentUserText(List<Turn> turns) {
        List<String> users = turns.stream().filter(t -> t.role().equals("user")).map(Turn::content).toList();
        return String.join(" ", users.subList(Math.max(0, users.size() - RED_FLAG_WINDOW), users.size()));
    }

    private void send(OutputStream out, String event, Object data) throws IOException {
        try {
            String frame = "event: " + event + "\ndata: " + json.writeValueAsString(data) + "\n\n";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
