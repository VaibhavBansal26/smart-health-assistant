package com.healthcare.app.appointment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /** Filtered + paged listing; patient and doctor are fetched in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"patient", "doctor"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    long countByDoctorIdAndStatusAndDateTimeAfter(Long doctorId, Appointment.Status status, LocalDateTime after);

    boolean existsByDoctorIdAndDateTimeAndStatus(Long doctorId, LocalDateTime dateTime, Appointment.Status status);

    /** Same check for rescheduling: ignore the appointment being moved. */
    boolean existsByDoctorIdAndDateTimeAndStatusAndIdNot(Long doctorId, LocalDateTime dateTime,
                                                          Appointment.Status status, Long id);
}
