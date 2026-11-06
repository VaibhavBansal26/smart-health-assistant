package com.healthcare.app.appointment;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    public record CreateRequest(
            @NotNull Long doctorId,
            @NotNull @Future(message = "Appointment must be in the future") LocalDateTime dateTime,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** Partial update (PATCH): send only what you want to change. At least one field is required. */
    public record UpdateRequest(
            @Future(message = "Appointment must be in the future") LocalDateTime dateTime,
            @Size(max = 500) @Pattern(regexp = "(?s).*\\S.*", message = "Reason cannot be blank") String reason) {
    }

    /** Counts for the dashboard, computed in the database so the client never needs the whole list. */
    public record SummaryResponse(long total, long booked, long completed, long cancelled, AppointmentResponse next) {
    }

    public record AppointmentResponse(
            Long id,
            Long patientId,
            String patientName,
            Long doctorId,
            String doctorName,
            String specialty,
            LocalDateTime dateTime,
            String reason,
            Appointment.Status status) {

        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(
                    a.getId(),
                    a.getPatient().getId(), a.getPatient().getFullName(),
                    a.getDoctor().getId(), a.getDoctor().getFullName(), a.getDoctor().getSpecialty(),
                    a.getDateTime(), a.getReason(), a.getStatus());
        }
    }
}
