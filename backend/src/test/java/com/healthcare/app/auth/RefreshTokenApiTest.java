package com.healthcare.app.auth;

import com.healthcare.app.support.ApiTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Sessions: short-lived access token + rotating HttpOnly refresh cookie")
class RefreshTokenApiTest extends ApiTest {

    @Autowired JdbcTemplate jdbc;

    private MvcResult login(String email) throws Exception {
        return post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rd!")).andExpect(status().isOk()).andReturn();
    }

    private String refreshValue(MvcResult r) {
        Cookie c = r.getResponse().getCookie(RefreshCookies.NAME);
        assertNotNull(c, "no refresh cookie in response");
        return c.getValue();
    }

    private ResultActions refresh(String cookieValue) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest");
        if (cookieValue != null) req.cookie(new Cookie(RefreshCookies.NAME, cookieValue));
        return mvc.perform(req);
    }

    private ResultActions logout(String cookieValue, boolean withHeader) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/auth/logout");
        if (withHeader) req.header("X-Requested-With", "XMLHttpRequest");
        if (cookieValue != null) req.cookie(new Cookie(RefreshCookies.NAME, cookieValue));
        return mvc.perform(req);
    }

    @Test
    void login_setsAHardenedRefreshCookie_andKeepsItOutOfTheBody() throws Exception {
        var patient = newPatient();
        MvcResult r = login(patient.email());

        Cookie c = r.getResponse().getCookie(RefreshCookies.NAME);
        assertTrue(c.isHttpOnly(), "JavaScript must not be able to read it");
        assertTrue(c.getSecure(), "must only travel over HTTPS");
        assertEquals("/api/auth", c.getPath(), "only sent to auth endpoints, never with ordinary API calls");
        assertEquals("Strict", c.getAttribute("SameSite"));
        assertEquals(14 * 24 * 3600, c.getMaxAge());
        assertThat(r.getResponse().getContentAsString(), not(containsString(c.getValue())));
        assertThat(body(r).get("token").asText(), not(emptyString()));
        assertEquals(900, body(r).get("expiresInSeconds").asInt()); // 15-minute access token
    }

    @Test
    void registration_alsoStartsASession() throws Exception {
        var r = post("/api/auth/register", null, Map.of("fullName", "New Person", "email", "reg-" + System.nanoTime() + "@test.dev",
                "password", "Passw0rd!", "role", "PATIENT")).andReturn();
        assertNotNull(r.getResponse().getCookie(RefreshCookies.NAME));
    }

    @Test
    void refresh_issuesANewAccessToken_andRotatesTheCookie() throws Exception {
        var patient = newPatient();
        String first = refreshValue(login(patient.email()));

        MvcResult r = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(patient.email())).andReturn();
        String second = refreshValue(r);

        assertNotEquals(first, second, "the refresh token must change on every use");
        get("/api/auth/me", body(r).get("token").asText()).andExpect(status().isOk()); // new access token works
    }

    @Test
    void refresh_requiresTheCookie_andTheCsrfHeader() throws Exception {
        String cookie = refreshValue(login(newPatient().email()));

        refresh(null).andExpect(status().isUnauthorized());
        // a cross-site HTML form cannot add this header, so omitting it is refused even with a valid cookie
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").cookie(new Cookie(RefreshCookies.NAME, cookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refresh_withAGarbageCookie_is401_andTellsTheBrowserToDropIt() throws Exception {
        MvcResult r = refresh("not-a-real-token").andExpect(status().isUnauthorized()).andReturn();
        assertEquals(0, r.getResponse().getCookie(RefreshCookies.NAME).getMaxAge(), "cookie should be cleared");
    }

    @Test
    void replayingAnOldToken_isDetectedAsTheft_andRevokesTheWholeSession() throws Exception {
        String stolen = refreshValue(login(newPatient().email()));
        String legit = refreshValue(refresh(stolen).andExpect(status().isOk()).andReturn());

        refresh(stolen).andExpect(status().isUnauthorized());   // attacker replays the old cookie
        refresh(legit).andExpect(status().isUnauthorized());    // the whole family is now dead, for both parties
    }

    @Test
    void logout_revokesTheSession_andClearsTheCookie() throws Exception {
        String cookie = refreshValue(login(newPatient().email()));

        MvcResult r = logout(cookie, true).andExpect(status().isNoContent()).andReturn();
        assertEquals(0, r.getResponse().getCookie(RefreshCookies.NAME).getMaxAge());
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_isHarmlessWithoutACookie_butStillNeedsTheHeader() throws Exception {
        logout(null, true).andExpect(status().isNoContent());
        logout("anything", false).andExpect(status().isForbidden());
    }

    @Test
    void anExpiredSession_isRejected() throws Exception {
        String cookie = refreshValue(login(newPatient().email()));
        jdbc.update("update refresh_tokens set expires_at = ? where token_hash = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)), RefreshTokenService.hash(cookie));
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void theDatabaseNeverHoldsTheTokenItself_onlyItsHash() throws Exception {
        String cookie = refreshValue(login(newPatient().email()));
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, cookie));
        assertEquals(1, jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, RefreshTokenService.hash(cookie)));
    }

    @Test
    void eachLoginIsAnIndependentSession_soLoggingOutOnePhoneKeepsTheLaptopSignedIn() throws Exception {
        var patient = newPatient();
        String phone = refreshValue(login(patient.email()));
        String laptop = refreshValue(login(patient.email()));

        logout(phone, true).andExpect(status().isNoContent());
        refresh(phone).andExpect(status().isUnauthorized());
        refresh(laptop).andExpect(status().isOk());
    }

    @Test
    void accessTokenCannotBeUsedAsARefreshToken() throws Exception {
        var patient = newPatient();
        refresh(patient.token()).andExpect(status().isUnauthorized());
    }

    private static void assertThat(String actual, org.hamcrest.Matcher<String> m) {
        org.hamcrest.MatcherAssert.assertThat(actual, m);
    }
}
