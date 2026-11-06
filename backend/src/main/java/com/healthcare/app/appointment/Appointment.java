package com.healthcare.app.appointment;

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

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.BOOKED;

    protected Appointment() {
    }

    public Appointment(User patient, User doctor, LocalDateTime dateTime, String reason) {
        this.patient = patient;
        this.doctor = doctor;
        this.dateTime = dateTime;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public User getPatient() { return patient; }
    public User getDoctor() { return doctor; }
    public LocalDateTime getDateTime() { return dateTime; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public void setReason(String reason) { this.reason = reason; }
}
