package com.healthcare.app.appointment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Databases created before the unique slot constraint existed hold BOOKED appointments with no slot_key, which the
 * constraint would not protect. On startup this gives them one (idempotent: it only touches rows still missing it).
 * If legacy data already contains two BOOKED appointments for the same doctor and time, the second cannot be keyed:
 * it is logged for a human to resolve and left alone rather than failing startup.
 */
@Component
public class SlotKeyBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SlotKeyBackfill.class);

    private final AppointmentRepository appointments;
    private final TransactionTemplate tx;

    public SlotKeyBackfill(AppointmentRepository appointments, PlatformTransactionManager txManager) {
        this.appointments = appointments;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        int fixed = 0;
        int conflicts = 0;
        for (Appointment legacy : appointments.findByStatusAndSlotKeyIsNull(Appointment.Status.BOOKED)) {
            try {
                tx.executeWithoutResult(status -> {
                    Appointment a = appointments.findById(legacy.getId()).orElseThrow();
                    a.refreshSlotKey();
                    appointments.flush();
                });
                fixed++;
            } catch (DataIntegrityViolationException duplicate) {
                conflicts++;
                log.warn("Appointment {} double-books a slot that another BOOKED appointment already holds; "
                        + "resolve it manually (cancel or reschedule one of them)", legacy.getId());
            }
        }
        if (fixed > 0 || conflicts > 0) {
            log.info("Slot-key backfill: {} appointment(s) protected, {} conflict(s) need attention", fixed, conflicts);
        }
    }
}
