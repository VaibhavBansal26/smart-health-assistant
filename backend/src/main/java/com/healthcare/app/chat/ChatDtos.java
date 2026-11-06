package com.healthcare.app.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatMessage(
            @NotBlank @Pattern(regexp = "user|assistant", message = "role must be user or assistant") String role,
            @NotBlank @Size(max = 2000, message = "Messages can be at most 2000 characters") String content) {
    }

    /** The conversation lives in the browser; the server keeps nothing (no health data at rest). */
    public record ChatRequest(@NotEmpty @Size(max = 60) List<@Valid ChatMessage> messages) {
    }

    public record StatusResponse(String provider, String model) {
    }
}
