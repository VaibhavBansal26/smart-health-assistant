package com.healthcare.app.crypto;

import com.healthcare.app.recommendation.MlClient;
import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reads the raw table contents with SQL, bypassing the app, to prove what an attacker holding the database would see. */
@DisplayName("Sensitive columns are encrypted at rest")
class EncryptionAtRestTest extends ApiTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void appointmentReasons_areCiphertextInTheDatabase_butPlaintextThroughTheApi() throws Exception {
        var patient = newPatient();
        long doctor = doctorIdBySpecialty("Cardiology");
        String secret = "Persistent chest tightness, family history 9137";
        long id = body(post("/api/appointments", patient.token(),
                Map.of("doctorId", doctor, "dateTime", slot(), "reason", secret)).andReturn()).get("id").asLong();

        String raw = jdbc.queryForObject("select reason from appointments where id = ?", String.class, id);
        assertTrue(raw.startsWith("enc:v1:"), "stored as: " + raw);
        assertFalse(raw.contains("chest") || raw.contains("9137"), "plaintext leaked into the database");

        get("/api/appointments", patient.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value(secret));
    }

    @Test
    void riskCheckMeasurements_areCiphertextInTheDatabase_butReadableThroughTheApi() throws Exception {
        var patient = newPatient();
        when(ml.predictDiabetesRisk(any())).thenReturn(new MlClient.RiskResult(55.0,
                List.of(new MlClient.Contribution("glucose", "2-hour glucose (OGTT)", "mg/dL", 143.0, 0.5)),
                List.of(), List.of(), "v1", "test", 0.8, 0.7));
        long id = body(post("/api/risk/diabetes", patient.token(), Map.of("age", 47, "glucose", 143, "bmi", 31.5)).andReturn())
                .get("id").asLong();

        String raw = jdbc.queryForObject("select inputs from risk_assessments where id = ?", String.class, id);
        assertTrue(raw.startsWith("enc:v1:"), "stored as: " + raw);
        assertFalse(raw.contains("glucose") || raw.contains("143"), "measurements leaked into the database");

        get("/api/risk/history", patient.token())
                .andExpect(jsonPath("$.content[0].age").value(47))
                .andExpect(jsonPath("$.content[0].glucose").value(143.0))
                .andExpect(jsonPath("$.content[0].bmi").value(31.5));
    }

    @Test
    void legacyPlaintextRows_writtenBeforeEncryption_stillReadFine() throws Exception {
        var patient = newPatient();
        long id = book(patient.token(), doctorIdBySpecialty("ENT"), slot());
        jdbc.update("update appointments set reason = ? where id = ?", "plain legacy text", id);

        get("/api/appointments", patient.token()).andExpect(jsonPath("$.content[0].reason").value("plain legacy text"));
    }

    @Test
    void aTamperedCiphertext_failsLoudly_insteadOfReturningGarbage() throws Exception {
        var patient = newPatient();
        long id = book(patient.token(), doctorIdBySpecialty("ENT"), slot());
        String raw = jdbc.queryForObject("select reason from appointments where id = ?", String.class, id);
        String tampered = raw.substring(0, raw.length() - 2) + (raw.endsWith("AA") ? "BB" : "AA");
        jdbc.update("update appointments set reason = ? where id = ?", tampered, id);

        var thrown = assertThrows(Exception.class, () -> get("/api/appointments", patient.token()));
        assertThat(messagesInChain(thrown), containsString("failed authentication"));
    }

    private static String messagesInChain(Throwable t) {
        StringBuilder all = new StringBuilder();
        for (; t != null; t = t.getCause()) all.append(t.getMessage()).append(" | ");
        return all.toString();
    }

    private static void assertThat(String actual, org.hamcrest.Matcher<String> m) {
        org.hamcrest.MatcherAssert.assertThat(actual, m);
    }
}
