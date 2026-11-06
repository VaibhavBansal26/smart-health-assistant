package com.healthcare.app.appointment;

import com.healthcare.app.appointment.AppointmentDtos.AppointmentResponse;
import com.healthcare.app.appointment.AppointmentDtos.CreateRequest;
import com.healthcare.app.appointment.AppointmentDtos.SummaryResponse;
import com.healthcare.app.appointment.AppointmentDtos.UpdateRequest;
import com.healthcare.app.common.PageResponse;
import com.healthcare.app.common.Paging;
import com.healthcare.app.exception.ApiException;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static com.healthcare.app.appointment.AppointmentSpecs.*;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Book, list, reschedule, cancel and complete appointments")
public class AppointmentController {

    private static final Set<String> SORTABLE = Set.of("dateTime", "status", "id");

    private final AppointmentRepository appointments;
    private final UserRepository users;

    public AppointmentController(AppointmentRepository appointments, UserRepository users) {
        this.appointments = appointments;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List my appointments (paged, filterable)",
            description = "Patients see their own bookings; doctors see the ones booked with them.")
    public PageResponse<AppointmentResponse> list(
            @Parameter(description = "Only this status") @RequestParam(required = false) Appointment.Status status,
            @Parameter(description = "On or after this date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "On or before this date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "field,direction. Fields: dateTime, status, id") @RequestParam(required = false) String sort,
            Authentication auth) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "'to' must not be before 'from'");
        }
        User me = currentUser(auth);
        var pageable = Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.ASC, "dateTime"));
        Page<Appointment> result = appointments.findAll(
                Specification.where(visibleTo(me)).and(hasStatus(status)).and(fromDate(from)).and(toDate(to)), pageable);
        return PageResponse.of(result, AppointmentResponse::from);
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    @Operation(summary = "Counts per status plus the next upcoming appointment",
            description = "Used by the dashboard; computed with COUNT queries rather than loading every appointment.")
    public SummaryResponse summary(Authentication auth) {
        User me = currentUser(auth);
        var mine = Specification.where(visibleTo(me));
        long booked = appointments.count(mine.and(hasStatus(Appointment.Status.BOOKED)));
        long completed = appointments.count(mine.and(hasStatus(Appointment.Status.COMPLETED)));
        long cancelled = appointments.count(mine.and(hasStatus(Appointment.Status.CANCELLED)));

        AppointmentResponse next = appointments.findAll(
                        mine.and(hasStatus(Appointment.Status.BOOKED)).and(startsAfter(LocalDateTime.now())),
                        PageRequest.of(0, 1, Sort.by("dateTime")))
                .stream().findFirst().map(AppointmentResponse::from).orElse(null);
        return new SummaryResponse(booked + completed + cancelled, booked, completed, cancelled, next);
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Book an appointment (patients only)",
            description = "409 if the doctor already has a booking at that time.")
    public AppointmentResponse create(@Valid @RequestBody CreateRequest req, Authentication auth) {
        User patient = currentUser(auth);
        User doctor = users.findById(req.doctorId())
                .filter(u -> u.getRole() == com.healthcare.app.user.Role.DOCTOR)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
        if (appointments.existsByDoctorIdAndDateTimeAndStatus(
                doctor.getId(), req.dateTime(), Appointment.Status.BOOKED)) {
            throw new ApiException(HttpStatus.CONFLICT, "That time slot is already booked");
        }
        Appointment saved = appointments.save(
                new Appointment(patient, doctor, req.dateTime(), req.reason().trim()));
        return AppointmentResponse.from(saved);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    @Transactional
    @Operation(summary = "Reschedule or edit a booked appointment (patients only)",
            description = "Partial update: send a new `dateTime`, a new `reason`, or both. "
                    + "409 if the appointment is not BOOKED or the new slot is taken.")
    public AppointmentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req, Authentication auth) {
        if (req.dateTime() == null && req.reason() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide a new dateTime, a new reason, or both");
        }
        Appointment a = findOwned(id, auth);
        if (a.getStatus() != Appointment.Status.BOOKED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only booked appointments can be changed");
        }
        if (req.dateTime() != null && !req.dateTime().equals(a.getDateTime())) {
            if (appointments.existsByDoctorIdAndDateTimeAndStatusAndIdNot(
                    a.getDoctor().getId(), req.dateTime(), Appointment.Status.BOOKED, a.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "That time slot is already booked");
            }
            a.setDateTime(req.dateTime());
        }
        if (req.reason() != null) {
            a.setReason(req.reason().trim());
        }
        return AppointmentResponse.from(a);
    }

    @PatchMapping("/{id}/cancel")
    @Transactional
    @Operation(summary = "Cancel a booked appointment (patient or doctor on it)")
    public AppointmentResponse cancel(@PathVariable Long id, Authentication auth) {
        Appointment a = findOwned(id, auth);
        if (a.getStatus() != Appointment.Status.BOOKED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only booked appointments can be cancelled");
        }
        a.setStatus(Appointment.Status.CANCELLED);
        return AppointmentResponse.from(a);
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    @Operation(summary = "Mark a booked appointment completed (doctor on it)")
    public AppointmentResponse complete(@PathVariable Long id, Authentication auth) {
        Appointment a = findOwned(id, auth);
        if (a.getStatus() != Appointment.Status.BOOKED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only booked appointments can be completed");
        }
        a.setStatus(Appointment.Status.COMPLETED);
        return AppointmentResponse.from(a);
    }

    private User currentUser(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    /** Loads an appointment and checks the caller is its patient or its doctor. */
    private Appointment findOwned(Long id, Authentication auth) {
        User me = currentUser(auth);
        Appointment a = appointments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        boolean isParticipant = a.getPatient().getId().equals(me.getId())
                || a.getDoctor().getId().equals(me.getId());
        if (!isParticipant) {
            // 404 rather than 403 so we don't reveal that the id exists
            throw new ApiException(HttpStatus.NOT_FOUND, "Appointment not found");
        }
        return a;
    }
}
