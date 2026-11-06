package com.healthcare.app.auth;

import com.healthcare.app.support.ApiTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Password reset")
class PasswordResetApiTest extends ApiTest {

    @Autowired JdbcTemplate jdbc;

    private String forgot(String email) throws Exception {
        return post("/api/auth/forgot-password", null, Map.of("email", email))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }

    private String resetToken(String email) {
        return mail.tokenIn(mail.latestTo(email, "Reset your password"));
    }

    private void reset(String token, String password, int expectedStatus) throws Exception {
        post("/api/auth/reset-password", null, Map.of("token", token, "newPassword", password))
                .andExpect(status().is(expectedStatus));
    }

    private void ageEmailTokens(String email) {
        jdbc.update("update email_tokens set created_at = ? where user_id = (select id from users where email = ?)",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(120)), email);
    }

    @Test
    void forgotPassword_answersIdentically_whetherOrNotTheAccountExists() throws Exception {
        var patient = newPatient();
        String ghost = "ghost-" + System.nanoTime() + "@test.dev";

        String known = forgot(patient.email());
        String unknown = forgot(ghost);

        assertEquals(known, unknown, "the response must not reveal whether the email is registered");
        assertEquals(0, mail.countTo(ghost), "nothing is sent to an unknown address");
        assertEquals(1, mail.to(patient.email()).stream().filter(m -> m.subject().contains("Reset")).count());
    }

    @Test
    void theEmail_containsASingleUseLinkToTheApp() throws Exception {
        var patient = newPatient();
        forgot(patient.email());
        var email = mail.latestTo(patient.email(), "Reset your password");
        org.hamcrest.MatcherAssert.assertThat(email.body(), containsString("http://app.test/reset-password?token="));
        org.hamcrest.MatcherAssert.assertThat(email.body(), containsString("expires in 1 hour"));
    }

    @Test
    void askingTwiceInAMinute_sendsOnlyOneEmail() throws Exception {
        var patient = newPatient();
        forgot(patient.email());
        forgot(patient.email());
        assertEquals(1, mail.to(patient.email()).stream().filter(m -> m.subject().contains("Reset")).count());
    }

    @Test
    void malformedEmail_isRejectedByValidation() throws Exception {
        post("/api/auth/forgot-password", null, Map.of("email", "not-an-email")).andExpect(status().isBadRequest());
    }

    @Test
    void resetting_changesThePassword_andTheLinkWorksOnlyOnce() throws Exception {
        var patient = newPatient();
        forgot(patient.email());
        String token = resetToken(patient.email());

        reset(token, "Brand-New-Pass1", 204);

        post("/api/auth/login", null, Map.of("email", patient.email(), "password", "Brand-New-Pass1")).andExpect(status().isOk());
        post("/api/auth/login", null, Map.of("email", patient.email(), "password", "Passw0rd!")).andExpect(status().isUnauthorized());
        post("/api/auth/reset-password", null, Map.of("token", token, "newPassword", "Another-Pass2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("invalid or has expired")));
    }

    @Test
    void aWeakPassword_isRejected_withoutUsingUpTheLink() throws Exception {
        var patient = newPatient();
        forgot(patient.email());
        String token = resetToken(patient.email());

        post("/api/auth/reset-password", null, Map.of("token", token, "newPassword", "abcdefgh"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.newPassword", containsString("letter and a number")));
        reset(token, "Strong-Pass-1", 204); // the same link still works
    }

    @Test
    void unknownAndExpiredLinks_areRejected() throws Exception {
        reset("not-a-real-token", "Strong-Pass-1", 400);

        var patient = newPatient();
        forgot(patient.email());
        String token = resetToken(patient.email());
        jdbc.update("update email_tokens set expires_at = ? where token_hash = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)), RefreshTokenService.hash(token));
        reset(token, "Strong-Pass-1", 400);
    }

    @Test
    void aNewerLink_replacesTheOlderOne() throws Exception {
        var patient = newPatient();
        forgot(patient.email());
        String first = resetToken(patient.email());
        ageEmailTokens(patient.email());
        forgot(patient.email());
        String second = resetToken(patient.email());

        assertNotEquals(first, second);
        reset(first, "Strong-Pass-1", 400);
        reset(second, "Strong-Pass-1", 204);
    }

    @Test
    void resetting_provesInboxControl_soItAlsoVerifiesTheEmail() throws Exception {
        var patient = newUnverifiedPatient();
        forgot(patient.email());
        reset(resetToken(patient.email()), "Strong-Pass-1", 204);

        var fresh = loginAs(patient.email(), "Strong-Pass-1");
        get("/api/auth/me", fresh.token()).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void resetting_signsTheAccountOutEverywhere() throws Exception {
        var patient = newPatient();
        String cookie = post("/api/auth/login", null, Map.of("email", patient.email(), "password", "Passw0rd!"))
                .andReturn().getResponse().getCookie(RefreshCookies.NAME).getValue();

        forgot(patient.email());
        reset(resetToken(patient.email()), "Strong-Pass-1", 204);

        mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie(RefreshCookies.NAME, cookie))).andExpect(status().isUnauthorized());
    }

    @Test
    void aVerificationLink_cannotBeUsedToResetAPassword() throws Exception {
        var patient = newUnverifiedPatient();
        String verifyToken = mail.tokenIn(mail.latestTo(patient.email(), "Verify"));
        reset(verifyToken, "Strong-Pass-1", 400);
    }
}
