package com.healthcare.app.message;

import com.healthcare.app.appointment.Appointment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record SendRequest(@NotBlank(message = "Write a message first") @Size(max = 1000, message = "Keep messages under 1000 characters") String body) {
    }

    public record MessageResponse(Long id, Long senderId, String senderName, boolean mine, String body, Instant createdAt) {
        static MessageResponse from(Message m, Long viewerId) {
            return new MessageResponse(m.getId(), m.getSender().getId(), m.getSender().getFullName(),
                    m.getSender().getId().equals(viewerId), m.getBody(), m.getCreatedAt());
        }
    }

    /** The conversation plus just enough about the appointment to label the screen. */
    public record ThreadResponse(Long appointmentId, String withName, String withRole, Appointment.Status status,
                                 LocalDateTime dateTime, boolean canSend, List<MessageResponse> messages) {
    }
}
