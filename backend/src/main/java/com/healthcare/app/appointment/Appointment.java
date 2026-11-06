package com.healthcare.app.appointment;

import com.healthcare.app.crypto.EncryptedStringConverter;
import com.healthcare.app.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
public class Appointment {

    public enum Status { BOOKED, CANCELLED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User patient;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User doctor;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    /** Free text about the person's health, so it is stored encrypted (ciphertext is longer than the 500-char input limit). */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.BOOKED;

    /**
     * "doctorId@dateTime" while the appointment is BOOKED, otherwise NULL. The UNIQUE constraint on this column is what
     * actually guarantees a doctor cannot be double-booked: the checks in the controller are only there for a friendly
     * message, and two simultaneous requests can both pass them. NULLs are not considered equal by unique constraints,
     * so cancelled/completed appointments never block a slot (and no partial-index support is needed).
     */
    @Column(name = "slot_key", unique = true, length = 80)
    private String slotKey;

    protected Appointment() {
    }

    public Appointment(User patient, User doctor, LocalDateTime dateTime, String reason) {
        this.patient = patient;
        this.doctor = doctor;
        this.dateTime = dateTime;
        this.reason = reason;
    }

    @PrePersist
    @PreUpdate
    public void refreshSlotKey() {
        this.slotKey = status == Status.BOOKED ? doctor.getId() + "@" + dateTime : null;
    }

    public Long getId() { return id; }
    public User getPatient() { return patient; }
    public User getDoctor() { return doctor; }
    public LocalDateTime getDateTime() { return dateTime; }
    public String getReason() { return reason; }
    public String getSlotKey() { return slotKey; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public void setReason(String reason) { this.reason = reason; }
}
