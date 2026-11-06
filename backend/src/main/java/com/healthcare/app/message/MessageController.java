package com.healthcare.app.message;

import com.healthcare.app.appointment.Appointment;
import com.healthcare.app.appointment.AppointmentRepository;
import com.healthcare.app.auth.VerifiedEmailGuard;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.message.MessageDtos.MessageResponse;
import com.healthcare.app.message.MessageDtos.SendRequest;
import com.healthcare.app.message.MessageDtos.ThreadResponse;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Private messages between the two people on an appointment. Nobody else can read or write them. */
@RestController
@RequestMapping("/api/appointments/{appointmentId}/messages")
@Tag(name = "Messages", description = "Private thread between the patient and the doctor of an appointment")
public class MessageController {

    /** Keeps a thread (and a poll response) bounded; a longer discussion belongs in a new appointment. */
    static final int MAX_MESSAGES_PER_THREAD = 200;

    private final MessageRepository messages;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final VerifiedEmailGuard verified;

    public MessageController(MessageRepository messages, AppointmentRepository appointments, UserRepository users,
                             VerifiedEmailGuard verified) {
        this.messages = messages;
        this.appointments = appointments;
        this.users = users;
        this.verified = verified;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Read the thread. Pass after=<last message id you have> to fetch only newer messages")
    public ThreadResponse thread(@PathVariable Long appointmentId, @RequestParam(defaultValue = "0") long after,
                                 Authentication auth) {
        User me = currentUser(auth);
        Appointment a = findParticipantAppointment(appointmentId, me);
        List<MessageResponse> list = messages.findByAppointmentIdAndIdGreaterThanOrderByIdAsc(a.getId(), Math.max(after, 0)).stream()
                .map(m -> MessageResponse.from(m, me.getId())).toList();
        User other = other(a, me);
        return new ThreadResponse(a.getId(), other.getFullName(), other.getRole().name(), a.getStatus(), a.getDateTime(),
                a.getStatus() != Appointment.Status.CANCELLED, list);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Send a message (not allowed once the appointment is cancelled)")
    public MessageResponse send(@PathVariable Long appointmentId, @Valid @RequestBody SendRequest req, Authentication auth) {
        verified.require(auth);
        User me = currentUser(auth);
        Appointment a = findParticipantAppointment(appointmentId, me);
        if (a.getStatus() == Appointment.Status.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "This appointment was cancelled, so the conversation is closed");
        }
        if (messages.countByAppointmentId(a.getId()) >= MAX_MESSAGES_PER_THREAD) {
            throw new ApiException(HttpStatus.CONFLICT, "This conversation has reached its message limit. Book a new appointment to continue");
        }
        String text = req.body().trim();
        if (text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Write a message first");
        }
        return MessageResponse.from(messages.save(new Message(a, me, text)), me.getId());
    }

    private User currentUser(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    /** 404 (not 403) for outsiders, so the existence of an appointment is not revealed. */
    private Appointment findParticipantAppointment(Long id, User me) {
        Appointment a = appointments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        boolean participant = a.getPatient().getId().equals(me.getId()) || a.getDoctor().getId().equals(me.getId());
        if (!participant) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Appointment not found");
        }
        return a;
    }

    private static User other(Appointment a, User me) {
        return a.getPatient().getId().equals(me.getId()) ? a.getDoctor() : a.getPatient();
    }
}
