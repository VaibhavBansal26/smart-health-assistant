package com.healthcare.app.message;

import com.healthcare.app.appointment.Appointment;
import com.healthcare.app.crypto.EncryptedStringConverter;
import com.healthcare.app.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/** One message in the private thread between the patient and the doctor of an appointment. */
@Entity
@Table(name = "messages", indexes = @Index(name = "idx_messages_appointment", columnList = "appointment_id,id"))
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Appointment appointment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User sender;

    /** Health information, so stored encrypted like appointment reasons (ciphertext is longer than the 1000-char limit). */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Message() {
    }

    public Message(Appointment appointment, User sender, String body) {
        this.appointment = appointment;
        this.sender = sender;
        this.body = body;
    }

    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public User getSender() { return sender; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
