package com.healthcare.app.auth;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth API")
class AuthApiTest extends ApiTest {

    private Map<String, Object> registration(String email) {
        return Map.of("fullName", "Maya Patel", "email", email, "password", "Passw0rd!", "role", "PATIENT");
    }

    @Test
    void register_createsAccount_andReturnsTokenAndUserWithoutPassword() throws Exception {
        post("/api/auth/register", null, registration("new-" + System.nanoTime() + "@test.dev"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void register_duplicateEmail_isConflict_evenWithDifferentCase() throws Exception {
        String email = "dup-" + System.nanoTime() + "@test.dev";
        post("/api/auth/register", null, registration(email)).andExpect(status().isCreated());
        post("/api/auth/register", null, registration(email.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void register_invalidInput_returnsFieldErrors() throws Exception {
        post("/api/auth/register", null, Map.of("fullName", "", "email", "nope", "password", "123", "role", "PATIENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    @Test
    void register_cannotCreateAdmin() throws Exception {
        post("/api/auth/register", null, Map.of("fullName", "Evil", "email", "a" + System.nanoTime() + "@test.dev",
                "password", "Passw0rd!", "role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_doctorMustProvideSpecialty() throws Exception {
        post("/api/auth/register", null, Map.of("fullName", "Dr X", "email", "d" + System.nanoTime() + "@test.dev",
                "password", "Passw0rd!", "role", "DOCTOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Specialty is required for doctors"));
    }

    @Test
    void login_withCorrectCredentials_returnsToken() throws Exception {
        String email = "login-" + System.nanoTime() + "@test.dev";
        post("/api/auth/register", null, registration(email));
        post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rd!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test
    void login_wrongPassword_isUnauthorized_withGenericMessage() throws Exception {
        String email = "wrong-" + System.nanoTime() + "@test.dev";
        post("/api/auth/register", null, registration(email));
        post("/api/auth/login", null, Map.of("email", email, "password", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_unknownUser_looksIdenticalToWrongPassword() throws Exception {
        post("/api/auth/login", null, Map.of("email", "ghost@test.dev", "password", "whatever123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void me_requiresAValidToken() throws Exception {
        get("/api/auth/me", null).andExpect(status().isUnauthorized());
        get("/api/auth/me", "garbage.token.value").andExpect(status().isUnauthorized());

        var patient = newPatient();
        get("/api/auth/me", patient.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(patient.email()));
    }

    @Test
    void malformedJson_isBadRequest_inTheStandardErrorShape() throws Exception {
        postRaw("/api/auth/login", null, "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
