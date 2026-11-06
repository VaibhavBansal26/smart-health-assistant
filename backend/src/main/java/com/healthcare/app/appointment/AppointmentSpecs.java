package com.healthcare.app.appointment;

import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Composable filters for the appointment list. Each returns null-safe "match all" when its input is absent. */
final class AppointmentSpecs {

    private AppointmentSpecs() {
    }

    /** The visibility rule: doctors see appointments booked with them, everyone else only their own. */
    static Specification<Appointment> visibleTo(User me) {
        String side = me.getRole() == Role.DOCTOR ? "doctor" : "patient";
        return (root, query, cb) -> cb.equal(root.get(side).get("id"), me.getId());
    }

    static Specification<Appointment> hasStatus(Appointment.Status status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Inclusive start date. */
    static Specification<Appointment> fromDate(LocalDate from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateTime"), from.atStartOfDay());
    }

    /** Inclusive end date (up to the end of that day). */
    static Specification<Appointment> toDate(LocalDate to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.get("dateTime"), to.plusDays(1).atStartOfDay());
    }

    static Specification<Appointment> startsAfter(LocalDateTime time) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateTime"), time);
    }
}
