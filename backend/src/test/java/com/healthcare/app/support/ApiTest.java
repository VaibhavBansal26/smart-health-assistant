package com.healthcare.app.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.app.recommendation.MlClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Base for API integration tests: boots the whole app (real security filter chain, real JPA on in-memory H2)
 * and talks to it through MockMvc. Only the Python ML service is replaced by a mock.
 * All subclasses share one cached Spring context, so the suite stays fast. Each test creates
 * its own users (unique emails) and time slots, so tests never depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTest {

    public static final String DOCTOR_PASSWORD = "Doctor@123"; // from DataSeeder
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @MockitoBean protected MlClient ml;

    public record Account(String email, String token, long id) {
    }

    // ---------- accounts ----------

    protected Account newPatient() throws Exception {
        String email = "patient" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.dev";
        JsonNode res = body(post("/api/auth/register", null,
                java.util.Map.of("fullName", "Test Patient", "email", email, "password", "Passw0rd!", "role", "PATIENT"))
                .andReturn());
        return new Account(email, res.get("token").asText(), res.get("user").get("id").asLong());
    }

    protected Account seededDoctor(String email) throws Exception {
        JsonNode res = body(post("/api/auth/login", null, java.util.Map.of("email", email, "password", DOCTOR_PASSWORD))
                .andReturn());
        return new Account(email, res.get("token").asText(), res.get("user").get("id").asLong());
    }

    protected long doctorIdBySpecialty(String specialty) throws Exception {
        Account patient = newPatient();
        return body(get("/api/doctors?specialty=" + specialty, patient.token()).andReturn())
                .get("content").get(0).get("id").asLong();
    }

    // ---------- requests ----------

    protected ResultActions get(String url, String token) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.get(url), token));
    }

    protected ResultActions post(String url, String token, Object payload) throws Exception {
        return mvc.perform(withBody(auth(MockMvcRequestBuilders.post(url), token), payload));
    }

    protected ResultActions postRaw(String url, String token, String rawBody) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.post(url), token).contentType(MediaType.APPLICATION_JSON).content(rawBody));
    }

    protected ResultActions patch(String url, String token, Object payload) throws Exception {
        return mvc.perform(withBody(auth(MockMvcRequestBuilders.patch(url), token), payload));
    }

    protected ResultActions delete(String url, String token) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.delete(url), token));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder b, Object payload) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    // ---------- domain helpers ----------

    /** A future slot nobody else uses (each call is 5 minutes after the previous one). */
    protected String slot() {
        return LocalDateTime.now().plusDays(10).plusMinutes(5L * SEQ.incrementAndGet()).withSecond(0).withNano(0).toString();
    }

    protected long book(String token, long doctorId, String dateTime) throws Exception {
        MvcResult r = post("/api/appointments", token,
                java.util.Map.of("doctorId", doctorId, "dateTime", dateTime, "reason", "Checkup")).andReturn();
        return body(r).get("id").asLong();
    }
}
