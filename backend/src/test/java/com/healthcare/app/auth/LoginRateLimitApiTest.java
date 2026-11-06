package com.healthcare.app.auth;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Login brute-force protection (HTTP)")
class LoginRateLimitApiTest extends ApiTest {

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return post("/api/auth/login", null, Map.of("email", email, "password", password));
    }

    @Test
    void afterFiveWrongPasswords_theAccountIsLocked_evenForTheCorrectPassword() throws Exception {
        var patient = newPatient();
        for (int i = 0; i < 5; i++) {
            login(patient.email(), "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(patient.email(), "wrong-again").andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("\\d+")))
                .andExpect(jsonPath("$.message", containsString("Too many failed")));
        // an attacker who finally guesses right must not get in while locked
        login(patient.email(), "Passw0rd!").andExpect(status().isTooManyRequests());
    }

    @Test
    void retryAfter_isAPositiveNumberOfSecondsWithinTheLockoutWindow() throws Exception {
        var patient = newPatient();
        for (int i = 0; i < 5; i++) login(patient.email(), "nope-" + i);
        long seconds = Long.parseLong(login(patient.email(), "nope").andReturn().getResponse().getHeader("Retry-After"));
        org.junit.jupiter.api.Assertions.assertTrue(seconds > 0 && seconds <= 15 * 60, "Retry-After was " + seconds);
    }

    @Test
    void lockingOneAccount_doesNotAffectAnother() throws Exception {
        var victim = newPatient();
        var bystander = newPatient();
        for (int i = 0; i < 6; i++) login(victim.email(), "nope-" + i);
        login(bystander.email(), "Passw0rd!").andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLogin_resetsTheFailureCount() throws Exception {
        var patient = newPatient();
        for (int i = 0; i < 4; i++) login(patient.email(), "nope-" + i).andExpect(status().isUnauthorized());
        login(patient.email(), "Passw0rd!").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) login(patient.email(), "again-" + i).andExpect(status().isUnauthorized());
        login(patient.email(), "Passw0rd!").andExpect(status().isOk());
    }

    @Test
    void unknownEmails_areLockedExactlyLikeRealOnes_soNothingLeaksAboutWhoIsRegistered() throws Exception {
        String ghost = "ghost-" + System.nanoTime() + "@test.dev";
        for (int i = 0; i < 5; i++) login(ghost, "whatever-" + i).andExpect(status().isUnauthorized());
        login(ghost, "whatever").andExpect(status().isTooManyRequests());
    }
}
