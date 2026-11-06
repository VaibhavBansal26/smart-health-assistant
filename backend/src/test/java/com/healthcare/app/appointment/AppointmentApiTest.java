package com.healthcare.app.appointment;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Appointments API")
class AppointmentApiTest extends ApiTest {

    private Map<String, Object> booking(long doctorId, String dateTime) {
        return Map.of("doctorId", doctorId, "dateTime", dateTime, "reason", "Checkup");
    }

    @Nested
    @DisplayName("booking")
    class Booking {

        @Test
        void patient_canBook_andSeesDoctorDetailsInTheResponse() throws Exception {
            var patient = newPatient();
            long doctor = doctorIdBySpecialty("Cardiology");

            post("/api/appointments", patient.token(), booking(doctor, slot()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("BOOKED"))
                    .andExpect(jsonPath("$.specialty").value("Cardiology"))
                    .andExpect(jsonPath("$.patientId").value(patient.id()));
        }

        @Test
        void sameDoctorAndTime_cannotBeBookedTwice() throws Exception {
            long doctor = doctorIdBySpecialty("Dermatology");
            String when = slot();
            post("/api/appointments", newPatient().token(), booking(doctor, when)).andExpect(status().isCreated());
            post("/api/appointments", newPatient().token(), booking(doctor, when))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("That time slot is already booked"));
        }

        @Test
        void aCancelledSlot_canBeBookedAgain() throws Exception {
            var first = newPatient();
            long doctor = doctorIdBySpecialty("Neurology");
            String when = slot();
            long id = book(first.token(), doctor, when);
            patch("/api/appointments/" + id + "/cancel", first.token(), null).andExpect(status().isOk());

            post("/api/appointments", newPatient().token(), booking(doctor, when)).andExpect(status().isCreated());
        }

        @Test
        void pastDate_isRejectedWithAFieldError() throws Exception {
            post("/api/appointments", newPatient().token(),
                    booking(doctorIdBySpecialty("ENT"), LocalDateTime.now().minusDays(1).toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.dateTime").value("Appointment must be in the future"));
        }

        @Test
        void unknownDoctor_is404() throws Exception {
            post("/api/appointments", newPatient().token(), booking(999_999, slot())).andExpect(status().isNotFound());
        }

        @Test
        void aPatientIsNotADoctor_soBookingWithTheirIdFails() throws Exception {
            var other = newPatient();
            post("/api/appointments", newPatient().token(), booking(other.id(), slot())).andExpect(status().isNotFound());
        }

        @Test
        void doctors_cannotBook() throws Exception {
            var doctor = seededDoctor("rohan.mehta@clinic.test");
            post("/api/appointments", doctor.token(), booking(doctor.id(), slot())).andExpect(status().isForbidden());
        }

        @Test
        void requiresLogin() throws Exception {
            post("/api/appointments", null, booking(1, slot())).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("listing: visibility, paging, filtering, sorting")
    class Listing {

        @Test
        void patients_seeOnlyTheirOwnAppointments() throws Exception {
            var alice = newPatient();
            var bob = newPatient();
            long doctor = doctorIdBySpecialty("Cardiology");
            long aliceAppt = book(alice.token(), doctor, slot());

            get("/api/appointments", alice.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(aliceAppt));
            get("/api/appointments", bob.token())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void doctors_seeAppointmentsBookedWithThem() throws Exception {
            var patient = newPatient();
            var doctor = seededDoctor("imran.qureshi@clinic.test");
            long id = book(patient.token(), doctor.id(), slot());

            get("/api/appointments?size=100&sort=id,desc", doctor.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == " + id + ")]", hasSize(1)));
        }

        @Test
        void results_arePaged_withMetadata() throws Exception {
            var patient = newPatient();
            long doctor = doctorIdBySpecialty("Endocrinology");
            for (int i = 0; i < 3; i++) book(patient.token(), doctor, slot());

            get("/api/appointments?page=0&size=2", patient.token())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true));
            get("/api/appointments?page=1&size=2", patient.token())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.hasNext").value(false));
            get("/api/appointments?page=5&size=2", patient.token())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void canFilterByStatus() throws Exception {
            var patient = newPatient();
            long doctor = doctorIdBySpecialty("Pulmonology");
            long keep = book(patient.token(), doctor, slot());
            long cancel = book(patient.token(), doctor, slot());
            patch("/api/appointments/" + cancel + "/cancel", patient.token(), null).andExpect(status().isOk());

            get("/api/appointments?status=CANCELLED", patient.token())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(cancel));
            get("/api/appointments?status=BOOKED", patient.token())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(keep));
        }

        @Test
        void canFilterByDateRange_inclusively() throws Exception {
            var patient = newPatient();
            long doctor = doctorIdBySpecialty("Gastroenterology");
            LocalDateTime base = LocalDateTime.now().plusDays(200).withHour(10).withMinute(0).withSecond(0).withNano(0);
            book(patient.token(), doctor, base.toString());                       // day 200
            book(patient.token(), doctor, base.plusDays(5).toString());           // day 205
            book(patient.token(), doctor, base.plusDays(10).withHour(23).withMinute(30).toString()); // late on day 210

            String d200 = base.toLocalDate().toString();
            String d205 = base.plusDays(5).toLocalDate().toString();
            String d210 = base.plusDays(10).toLocalDate().toString();
            get("/api/appointments?from=" + d205 + "&to=" + d205, patient.token()).andExpect(jsonPath("$.totalElements").value(1));
            get("/api/appointments?from=" + d200 + "&to=" + d210, patient.token()).andExpect(jsonPath("$.totalElements").value(3));
            get("/api/appointments?from=" + d210, patient.token()).andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void canSortAscendingAndDescending() throws Exception {
            var patient = newPatient();
            long doctor = doctorIdBySpecialty("Psychiatry");
            String early = slot();
            String late = slot();
            book(patient.token(), doctor, late);
            book(patient.token(), doctor, early);

            get("/api/appointments?sort=dateTime,asc", patient.token()).andExpect(jsonPath("$.content[0].dateTime").value(startsWith(early)));
            get("/api/appointments?sort=dateTime,desc", patient.token()).andExpect(jsonPath("$.content[0].dateTime").value(startsWith(late)));
        }

        @Test
        void badQueryParameters_areRejectedClearly() throws Exception {
            String token = newPatient().token();
            get("/api/appointments?status=NOPE", token).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid value for 'status'"));
            get("/api/appointments?size=0", token).andExpect(status().isBadRequest());
            get("/api/appointments?size=101", token).andExpect(status().isBadRequest());
            get("/api/appointments?page=-1", token).andExpect(status().isBadRequest());
            get("/api/appointments?from=2030-02-01&to=2030-01-01", token).andExpect(status().isBadRequest());
            get("/api/appointments?from=not-a-date", token).andExpect(status().isBadRequest());
        }

        @Test
        void sorting_isRestrictedToAWhitelist() throws Exception {
            String token = newPatient().token();
            get("/api/appointments?sort=patient.password,asc", token).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Cannot sort by")));
            get("/api/appointments?sort=dateTime,sideways", token).andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("summary")
    class Summary {

        @Test
        void countsEachStatus_andReturnsTheEarliestUpcomingBooking() throws Exception {
            var patient = newPatient();
            long doctor = doctorIdBySpecialty("Cardiology");
            String first = slot();
            long a = book(patient.token(), doctor, first);
            book(patient.token(), doctor, slot());
            long cancelled = book(patient.token(), doctor, slot());
            patch("/api/appointments/" + cancelled + "/cancel", patient.token(), null);

            get("/api/appointments/summary", patient.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(3))
                    .andExpect(jsonPath("$.booked").value(2))
                    .andExpect(jsonPath("$.cancelled").value(1))
                    .andExpect(jsonPath("$.completed").value(0))
                    .andExpect(jsonPath("$.next.id").value(a));
        }

        @Test
        void aNewPatient_hasZeroesAndNoNextAppointment() throws Exception {
            get("/api/appointments/summary", newPatient().token())
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.next").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("cancel and complete")
    class Lifecycle {

        @Test
        void patient_canCancel_butOnlyOnce() throws Exception {
            var patient = newPatient();
            long id = book(patient.token(), doctorIdBySpecialty("ENT"), slot());
            patch("/api/appointments/" + id + "/cancel", patient.token(), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            patch("/api/appointments/" + id + "/cancel", patient.token(), null).andExpect(status().isConflict());
        }

        @Test
        void strangers_cannotCancel_andGet404NotForbidden() throws Exception {
            long id = book(newPatient().token(), doctorIdBySpecialty("ENT"), slot());
            patch("/api/appointments/" + id + "/cancel", newPatient().token(), null).andExpect(status().isNotFound());
        }

        @Test
        void onlyTheDoctorOnTheAppointment_canComplete() throws Exception {
            var patient = newPatient();
            var doctor = seededDoctor("sana.khan@clinic.test");
            long id = book(patient.token(), doctor.id(), slot());

            patch("/api/appointments/" + id + "/complete", patient.token(), null).andExpect(status().isForbidden());
            patch("/api/appointments/" + id + "/complete", seededDoctor("arjun.rao@clinic.test").token(), null)
                    .andExpect(status().isNotFound());
            patch("/api/appointments/" + id + "/complete", doctor.token(), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
            patch("/api/appointments/" + id + "/cancel", patient.token(), null).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("reschedule / edit (PATCH)")
    class Reschedule {

        @Test
        void patient_canMoveAnAppointment() throws Exception {
            var patient = newPatient();
            long id = book(patient.token(), doctorIdBySpecialty("Dermatology"), slot());
            String newTime = slot();

            patch("/api/appointments/" + id, patient.token(), Map.of("dateTime", newTime))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dateTime").value(startsWith(newTime)))
                    .andExpect(jsonPath("$.reason").value("Checkup")); // untouched
        }

        @Test
        void canChangeOnlyTheReason() throws Exception {
            var patient = newPatient();
            long id = book(patient.token(), doctorIdBySpecialty("Dermatology"), slot());
            patch("/api/appointments/" + id, patient.token(), Map.of("reason", "  Rash got worse  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reason").value("Rash got worse"));
        }

        @Test
        void movingFreesTheOldSlot_andBlocksTheNewOne() throws Exception {
            long doctor = doctorIdBySpecialty("Orthopedics");
            var mover = newPatient();
            String oldSlot = slot();
            String newSlot = slot();
            long id = book(mover.token(), doctor, oldSlot);
            patch("/api/appointments/" + id, mover.token(), Map.of("dateTime", newSlot)).andExpect(status().isOk());

            post("/api/appointments", newPatient().token(), booking(doctor, oldSlot)).andExpect(status().isCreated());
            post("/api/appointments", newPatient().token(), booking(doctor, newSlot)).andExpect(status().isConflict());
        }

        @Test
        void movingOntoSomeoneElsesSlot_isConflict() throws Exception {
            long doctor = doctorIdBySpecialty("Orthopedics");
            String taken = slot();
            book(newPatient().token(), doctor, taken);
            var mover = newPatient();
            long id = book(mover.token(), doctor, slot());

            patch("/api/appointments/" + id, mover.token(), Map.of("dateTime", taken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("That time slot is already booked"));
        }

        @Test
        void sendingTheSameTime_isNotAConflictWithItself() throws Exception {
            var patient = newPatient();
            String when = slot();
            long id = book(patient.token(), doctorIdBySpecialty("Orthopedics"), when);
            patch("/api/appointments/" + id, patient.token(), Map.of("dateTime", when)).andExpect(status().isOk());
        }

        @Test
        void invalidChanges_areRejected() throws Exception {
            var patient = newPatient();
            long id = book(patient.token(), doctorIdBySpecialty("Orthopedics"), slot());
            String url = "/api/appointments/" + id;

            patch(url, patient.token(), Map.of()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Provide")));
            patch(url, patient.token(), Map.of("dateTime", LocalDateTime.now().minusHours(1).toString()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.dateTime").exists());
            patch(url, patient.token(), Map.of("reason", "   ")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.reason").exists());
            patch(url, patient.token(), Map.of("reason", "x".repeat(501))).andExpect(status().isBadRequest());
        }

        @Test
        void onlyBookedAppointments_canBeChanged() throws Exception {
            var patient = newPatient();
            long id = book(patient.token(), doctorIdBySpecialty("Orthopedics"), slot());
            patch("/api/appointments/" + id + "/cancel", patient.token(), null);
            patch("/api/appointments/" + id, patient.token(), Map.of("dateTime", slot())).andExpect(status().isConflict());
        }

        @Test
        void strangers_doctors_andAnonymous_cannotChangeIt() throws Exception {
            var owner = newPatient();
            var doctor = seededDoctor("asha.verma@clinic.test");
            long id = book(owner.token(), doctor.id(), slot());
            String url = "/api/appointments/" + id;
            Map<String, Object> change = Map.of("dateTime", slot());

            patch(url, newPatient().token(), change).andExpect(status().isNotFound());
            patch(url, doctor.token(), change).andExpect(status().isForbidden());
            patch(url, null, change).andExpect(status().isUnauthorized());
            patch("/api/appointments/999999", owner.token(), change).andExpect(status().isNotFound());
        }
    }
}
