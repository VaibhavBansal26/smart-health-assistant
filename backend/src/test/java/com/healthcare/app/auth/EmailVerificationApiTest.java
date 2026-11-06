package com.healthcare.app.auth;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Email verification")
class EmailVerificationApiTest extends ApiTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void registering_sendsOneVerificationEmail_withALinkToTheApp() throws Exception {
        var patient = newUnverifiedPatient();

        assertEquals(1, mail.countTo(patient.email()));
        var email = mail.latestTo(patient.email(), "Verify your email");
        assertThat(email.body(), containsString("http://app.test/verify-email?token="));
        assertThat(email.body(), containsString("expires in 24 hour"));
        get("/api/auth/me", patient.token()).andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void anUnverifiedUser_canBrowse_butNotUseFeaturesThatHandleHealthData() throws Exception {
        var patient = newUnverifiedPatient();

        get("/api/doctors", patient.token()).andExpect(status().isOk());
        get("/api/appointments", patient.token()).andExpect(status().isOk());
        get("/api/auth/me", patient.token()).andExpect(status().isOk());

        long doctor = doctorIdBySpecialty("Cardiology");
        String gate = "verify your email";
        post("/api/appointments", patient.token(), Map.of("doctorId", doctor, "dateTime", slot(), "reason", "x"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message", containsString(gate)));
        post("/api/risk/diabetes", patient.token(), Map.of("age", 40, "glucose", 100))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message", containsString(gate)));
        post("/api/recommendations", patient.token(), Map.of("symptoms", "headache for three days"))
                .andExpect(status().isForbidden());
        post("/api/chat/stream", patient.token(), Map.of("messages", List.of(Map.of("role", "user", "content", "hello there"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifying_unlocksTheFeatures() throws Exception {
        var patient = newUnverifiedPatient();
        long doctor = doctorIdBySpecialty("Cardiology");
        post("/api/appointments", patient.token(), Map.of("doctorId", doctor, "dateTime", slot(), "reason", "x"))
                .andExpect(status().isForbidden());

        verifyEmailOf(patient.email());

        get("/api/auth/me", patient.token()).andExpect(jsonPath("$.emailVerified").value(true));
        post("/api/appointments", patient.token(), Map.of("doctorId", doctor, "dateTime", slot(), "reason", "x"))
                .andExpect(status().isCreated());
    }

    @Test
    void aLink_worksOnce() throws Exception {
        var patient = newUnverifiedPatient();
        String token = mail.tokenIn(mail.latestTo(patient.email(), "Verify"));

        post("/api/auth/verify-email", null, Map.of("token", token)).andExpect(status().isNoContent());
        post("/api/auth/verify-email", null, Map.of("token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("invalid or has expired")));
    }

    @Test
    void unknownBlankAndExpiredTokens_areRejected_withTheSameGenericError() throws Exception {
        post("/api/auth/verify-email", null, Map.of("token", "not-a-real-token"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("invalid or has expired")));
        post("/api/auth/verify-email", null, Map.of("token", "")).andExpect(status().isBadRequest());

        var patient = newUnverifiedPatient();
        String token = mail.tokenIn(mail.latestTo(patient.email(), "Verify"));
        jdbc.update("update email_tokens set expires_at = ? where token_hash = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), RefreshTokenService.hash(token));
        post("/api/auth/verify-email", null, Map.of("token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("invalid or has expired")));
    }

    @Test
    void aPasswordResetLink_cannotBeUsedToVerifyAnEmail() throws Exception {
        var patient = newUnverifiedPatient();
        post("/api/auth/forgot-password", null, Map.of("email", patient.email())).andExpect(status().isAccepted());
        String resetToken = mail.tokenIn(mail.latestTo(patient.email(), "Reset your password"));

        post("/api/auth/verify-email", null, Map.of("token", resetToken)).andExpect(status().isBadRequest());
    }

    @Test
    void tokensAreStoredHashed_notAsTheLinkValue() throws Exception {
        var patient = newUnverifiedPatient();
        String token = mail.tokenIn(mail.latestTo(patient.email(), "Verify"));
        assertEquals(0, jdbc.queryForObject("select count(*) from email_tokens where token_hash = ?", Integer.class, token));
        assertEquals(1, jdbc.queryForObject("select count(*) from email_tokens where token_hash = ?", Integer.class, RefreshTokenService.hash(token)));
    }

    @Test
    void resending_sendsANewLink_andInvalidatesTheOldOne() throws Exception {
        var patient = newUnverifiedPatient();
        String first = mail.tokenIn(mail.latestTo(patient.email(), "Verify"));
        ageEmailTokens(patient.email()); // pretend a minute has passed

        post("/api/auth/resend-verification", patient.token(), Map.of()).andExpect(status().isNoContent());

        assertEquals(2, mail.countTo(patient.email()));
        String second = mail.tokenIn(mail.latestTo(patient.email(), "Verify"));
        assertNotEquals(first, second);
        post("/api/auth/verify-email", null, Map.of("token", first)).andExpect(status().isBadRequest());
        post("/api/auth/verify-email", null, Map.of("token", second)).andExpect(status().isNoContent());
    }

    @Test
    void resending_isThrottled_soItCannotSpamAnInbox() throws Exception {
        var patient = newUnverifiedPatient(); // registration already sent one just now
        post("/api/auth/resend-verification", patient.token(), Map.of())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")));
        assertEquals(1, mail.countTo(patient.email()));
    }

    @Test
    void resending_forAnAlreadyVerifiedUser_isANoOp_andNeedsLogin() throws Exception {
        var patient = newPatient();
        int before = mail.countTo(patient.email());
        post("/api/auth/resend-verification", patient.token(), Map.of()).andExpect(status().isNoContent());
        assertEquals(before, mail.countTo(patient.email()));
        post("/api/auth/resend-verification", null, Map.of()).andExpect(status().isUnauthorized());
    }

    /** Moves existing email tokens a minute into the past so the resend cooldown does not apply. */
    private void ageEmailTokens(String email) {
        jdbc.update("update email_tokens set created_at = ? where user_id = (select id from users where email = ?)",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(120)), email);
    }

    private static void assertThat(String actual, org.hamcrest.Matcher<String> m) {
        org.hamcrest.MatcherAssert.assertThat(actual, m);
    }
}
