package com.healthcare.app.config;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@DisplayName("Security response headers")
class SecurityHeadersTest extends ApiTest {

    @Test
    void everyApiResponse_carriesTheHardeningHeaders_evenErrors() throws Exception {
        get("/api/doctors", null) // 401, still must be hardened
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Permissions-Policy", containsString("microphone=()")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void hsts_isOnlySentOverHttps() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/doctors").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
        mvc.perform(MockMvcRequestBuilders.get("/api/doctors"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void swaggerUi_isExemptFromTheStrictCsp_soTheDocsPageStillWorks() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/swagger-ui/index.html"))
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void cors_allowsOnlyConfiguredOrigins_withCredentials() throws Exception {
        mvc.perform(MockMvcRequestBuilders.options("/api/auth/refresh")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-requested-with"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(MockMvcRequestBuilders.options("/api/auth/refresh")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
}
