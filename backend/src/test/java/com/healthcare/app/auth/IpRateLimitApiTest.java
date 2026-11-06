package com.healthcare.app.auth;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own context (and own in-memory database) because it lowers the per-IP budget to 3. */
@TestPropertySource(properties = {
        "app.auth.rate-limit.ip-max-requests=3",
        "spring.datasource.url=jdbc:h2:mem:iplimit;DB_CLOSE_DELAY=-1"})
@DisplayName("Per-IP limit on auth endpoints (HTTP)")
class IpRateLimitApiTest extends ApiTest {

    @Test
    void loginAndRegistrationShareOneBudgetPerIp() throws Exception {
        post("/api/auth/login", null, Map.of("email", "a@x.test", "password", "whatever1")).andExpect(status().isUnauthorized());
        post("/api/auth/login", null, Map.of("email", "b@x.test", "password", "whatever1")).andExpect(status().isUnauthorized());
        post("/api/auth/register", null, Map.of("fullName", "A B", "email", "c" + System.nanoTime() + "@x.test",
                "password", "Passw0rd!", "role", "PATIENT")).andExpect(status().isCreated());

        post("/api/auth/login", null, Map.of("email", "d@x.test", "password", "whatever1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message", containsString("Too many requests")));
        post("/api/auth/register", null, Map.of("fullName", "A B", "email", "e@x.test",
                "password", "Passw0rd!", "role", "PATIENT")).andExpect(status().isTooManyRequests());
    }
}
