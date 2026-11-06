package com.healthcare.app.config;

import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own context: REQUIRE_HTTPS=true, as in production. */
@TestPropertySource(properties = {
        "app.security.require-https=true",
        "spring.datasource.url=jdbc:h2:mem:requirehttps;DB_CLOSE_DELAY=-1"})
@DisplayName("HTTPS enforcement (REQUIRE_HTTPS=true)")
class RequireHttpsTest extends ApiTest {

    @Test
    void plainHttpRequests_areRedirectedToHttps() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/doctors"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://")));
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void httpsRequests_areServedNormally() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/doctors").secure(true)).andExpect(status().isUnauthorized()); // reached the app
    }
}
