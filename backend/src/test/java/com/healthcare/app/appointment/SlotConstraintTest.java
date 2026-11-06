package com.healthcare.app.appointment;

import com.healthcare.app.support.ApiTest;
import com.healthcare.app.user.Role;
import com.healthcare.app.user.User;
import com.healthcare.app.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talks to the repository directly, bypassing every check in the controller, to prove the DATABASE refuses a
 * double booking. This is the guarantee that holds even when two requests race past the application-level check.
 */
@DisplayName("Double-booking is impossible at the database level")
class SlotConstraintTest extends ApiTest {

    @Autowired AppointmentRepository appointments;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired SlotKeyBackfill backfill;

    private User patient() {
        return users.save(new User("DB Patient", "dbp-" + System.nanoTime() + "@test.dev", "x", Role.PATIENT, null));
    }

    private List<User> doctors() {
        return users.findByRole(Role.DOCTOR);
    }

    private LocalDateTime when(int minutes) {
        return LocalDateTime.now().plusDays(400).plusMinutes(minutes).withSecond(0).withNano(0);
    }

    private Appointment booked(User patient, User doctor, LocalDateTime time) {
        return appointments.saveAndFlush(new Appointment(patient, doctor, time, "reason"));
    }

    @Test
    void aSecondBookedAppointmentForTheSameDoctorAndTime_isRejectedByTheDatabase() {
        User doctor = doctors().get(0);
        LocalDateTime time = when(1);
        booked(patient(), doctor, time);
        assertThrows(DataIntegrityViolationException.class, () -> booked(patient(), doctor, time));
    }

    @Test
    void theSameTimeWithADifferentDoctor_orADifferentTimeWithTheSameDoctor_isFine() {
        User first = doctors().get(0);
        User second = doctors().get(1);
        LocalDateTime time = when(2);
        booked(patient(), first, time);
        assertDoesNotThrow(() -> booked(patient(), second, time));
        assertDoesNotThrow(() -> booked(patient(), first, time.plusMinutes(30)));
    }

    @Test
    void cancellingFreesTheSlot() {
        User doctor = doctors().get(0);
        LocalDateTime time = when(3);
        Appointment a = booked(patient(), doctor, time);
        assertNotNull(a.getSlotKey());

        a.setStatus(Appointment.Status.CANCELLED);
        appointments.saveAndFlush(a);
        assertNull(appointments.findById(a.getId()).orElseThrow().getSlotKey());

        assertDoesNotThrow(() -> booked(patient(), doctor, time));
    }

    @Test
    void manyCancelledAppointmentsMayShareASlot_becauseNullsNeverCollide() {
        User doctor = doctors().get(0);
        LocalDateTime time = when(4);
        for (int i = 0; i < 3; i++) {
            Appointment a = booked(patient(), doctor, time);
            a.setStatus(Appointment.Status.CANCELLED);
            appointments.saveAndFlush(a);
        }
        assertDoesNotThrow(() -> booked(patient(), doctor, time)); // and a live one still fits
    }

    @Test
    void movingAnAppointmentOntoATakenSlot_isRejectedByTheDatabase() {
        User doctor = doctors().get(0);
        booked(patient(), doctor, when(5));
        Appointment mover = booked(patient(), doctor, when(6));
        mover.setDateTime(when(5));
        assertThrows(DataIntegrityViolationException.class, () -> appointments.saveAndFlush(mover));
    }

    @Test
    void backfill_protectsLegacyRowsThatHadNoSlotKey() {
        User doctor = doctors().get(0);
        Appointment a = booked(patient(), doctor, when(7));
        jdbc.update("update appointments set slot_key = null where id = ?", a.getId()); // simulate a pre-constraint row

        backfill.run(null);

        assertEquals(doctor.getId() + "@" + when(7), appointments.findById(a.getId()).orElseThrow().getSlotKey());
    }

    @Test
    void backfill_doesNotCrash_onLegacyDataThatAlreadyDoubleBooks() {
        User doctor = doctors().get(0);
        Appointment a = booked(patient(), doctor, when(8));
        Appointment b = booked(patient(), doctor, when(9));
        jdbc.update("update appointments set slot_key = null where id in (?, ?)", a.getId(), b.getId());
        jdbc.update("update appointments set date_time = ? where id = ?", java.sql.Timestamp.valueOf(when(8)), b.getId());

        assertDoesNotThrow(() -> backfill.run(null));

        long keyed = List.of(a.getId(), b.getId()).stream()
                .filter(id -> appointments.findById(id).orElseThrow().getSlotKey() != null).count();
        assertEquals(1, keyed, "exactly one of the two duplicates gets the key; the other is left for a human");
    }
}
