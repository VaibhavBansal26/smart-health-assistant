package com.healthcare.app.message;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Doctor-patient messages API")
class MessageApiTest extends ApiTest {

    @Autowired JdbcTemplate jdbc;

    private record Setup(Account patient, Account doctor, long appointmentId) {
    }

    /** A patient with a booking on a seeded doctor, and that doctor's login. */
    private Setup booked() throws Exception {
        var patient = newPatient();
        var doctor = seededDoctor("asha.verma@clinic.test");
        return new Setup(patient, doctor, book(patient.token(), doctor.id(), slot()));
    }

    private String url(Setup s) {
        return "/api/appointments/" + s.appointmentId() + "/messages";
    }

    @Test
    @DisplayName("both sides read the same thread, each seeing which messages are theirs")
    void patientAndDoctor_exchangeMessages() throws Exception {
        var s = booked();
        post(url(s), s.patient().token(), Map.of("body", "My chest feels tight on stairs")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true));
        post(url(s), s.doctor().token(), Map.of("body", "Thanks, any pain in your left arm?")).andExpect(status().isCreated());

        get(url(s), s.doctor().token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.withRole").value("PATIENT"))
                .andExpect(jsonPath("$.canSend").value(true))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].body").value("My chest feels tight on stairs"))
                .andExpect(jsonPath("$.messages[0].mine").value(false))
                .andExpect(jsonPath("$.messages[1].mine").value(true));
        get(url(s), s.patient().token())
                .andExpect(jsonPath("$.withRole").value("DOCTOR"))
                .andExpect(jsonPath("$.messages[0].mine").value(true));
    }

    @Test
    @DisplayName("after=<id> returns only newer messages (used for polling)")
    void after_returnsOnlyNewer() throws Exception {
        var s = booked();
        long first = body(post(url(s), s.patient().token(), Map.of("body", "one")).andReturn()).get("id").asLong();
        post(url(s), s.doctor().token(), Map.of("body", "two")).andExpect(status().isCreated());
        get(url(s) + "?after=" + first, s.patient().token())
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].body").value("two"));
    }

    @Test
    @DisplayName("outsiders get 404 on both reading and writing, so they cannot even learn the appointment exists")
    void outsiders_cannotReadOrWrite() throws Exception {
        var s = booked();
        post(url(s), s.patient().token(), Map.of("body", "private")).andExpect(status().isCreated());
        var otherPatient = newPatient();
        var otherDoctor = seededDoctor("priya.nair@clinic.test");
        for (var outsider : new Account[]{otherPatient, otherDoctor}) {
            get(url(s), outsider.token()).andExpect(status().isNotFound());
            post(url(s), outsider.token(), Map.of("body", "hello")).andExpect(status().isNotFound());
        }
        get("/api/appointments/999999999/messages", s.patient().token()).andExpect(status().isNotFound());
    }

    @Test
    void requiresSignIn() throws Exception {
        var s = booked();
        get(url(s), null).andExpect(status().isUnauthorized());
        post(url(s), null, Map.of("body", "hi")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a cancelled appointment keeps its history readable but closes the conversation")
    void cancelled_isReadOnly() throws Exception {
        var s = booked();
        post(url(s), s.patient().token(), Map.of("body", "before cancelling")).andExpect(status().isCreated());
        patch("/api/appointments/" + s.appointmentId() + "/cancel", s.patient().token(), Map.of()).andExpect(status().isOk());
        get(url(s), s.doctor().token()).andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.messages", hasSize(1)));
        post(url(s), s.doctor().token(), Map.of("body", "too late")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a completed appointment still allows follow-up questions")
    void completed_allowsFollowUp() throws Exception {
        var s = booked();
        patch("/api/appointments/" + s.appointmentId() + "/complete", s.doctor().token(), Map.of()).andExpect(status().isOk());
        post(url(s), s.patient().token(), Map.of("body", "Quick follow-up question")).andExpect(status().isCreated());
    }

    @Test
    void rejectsBlankAndTooLongMessages() throws Exception {
        var s = booked();
        post(url(s), s.patient().token(), Map.of("body", "   ")).andExpect(status().isBadRequest());
        post(url(s), s.patient().token(), Map.of("body", "x".repeat(1001))).andExpect(status().isBadRequest());
        post(url(s), s.patient().token(), Map.of("body", "x".repeat(1000))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("message text is encrypted in the database")
    void bodyIsEncryptedAtRest() throws Exception {
        var s = booked();
        String secret = "I was diagnosed with something private";
        long id = body(post(url(s), s.patient().token(), Map.of("body", secret)).andReturn()).get("id").asLong();
        String raw = jdbc.queryForObject("select body from messages where id = ?", String.class, id);
        assertTrue(raw.startsWith("enc:v1:"), "stored as: " + raw);
        assertFalse(raw.contains("diagnosed"));
        get(url(s), s.doctor().token()).andExpect(jsonPath("$.messages[0].body").value(secret));
    }

    @Test
    @DisplayName("a thread is capped so it cannot grow without bound")
    void threadIsCapped() throws Exception {
        var s = booked();
        for (int i = 0; i < MessageController.MAX_MESSAGES_PER_THREAD; i++) {
            post(url(s), s.patient().token(), Map.of("body", "m" + i)).andExpect(status().isCreated());
        }
        post(url(s), s.doctor().token(), Map.of("body", "one too many")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an unverified email cannot send messages")
    void unverified_cannotSend() throws Exception {
        var s = booked();
        // a doctor/patient pair already exists; a fresh unverified user is not a participant, so use their own flow:
        var unverified = newUnverifiedPatient();
        post("/api/appointments/" + s.appointmentId() + "/messages", unverified.token(), Map.of("body", "hi"))
                .andExpect(status().isForbidden());
    }
}
