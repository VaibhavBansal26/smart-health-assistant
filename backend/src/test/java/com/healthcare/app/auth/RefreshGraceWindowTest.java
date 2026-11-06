package com.healthcare.app.auth;

import com.healthcare.app.support.ApiTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Two browser tabs refreshing at the same moment must not be mistaken for a thief. Own context: grace = 30 s. */
@TestPropertySource(properties = {
        "app.auth.refresh-reuse-grace-seconds=30",
        "spring.datasource.url=jdbc:h2:mem:refreshgrace;DB_CLOSE_DELAY=-1"})
@DisplayName("Refresh token grace window (multiple tabs)")
class RefreshGraceWindowTest extends ApiTest {

    private MvcResult refresh(String cookieValue) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie(RefreshCookies.NAME, cookieValue))).andReturn();
    }

    private String login(String email) throws Exception {
        return post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rd!")).andReturn()
                .getResponse().getCookie(RefreshCookies.NAME).getValue();
    }

    @Test
    void aTokenReusedRightAfterRotation_isTreatedAsAnotherTab_andBothNewTokensWork() throws Exception {
        String original = login(newPatient().email());

        MvcResult tabA = refresh(original);
        MvcResult tabB = refresh(original); // second tab still holds the old cookie
        assertEquals(200, tabA.getResponse().getStatus());
        assertEquals(200, tabB.getResponse().getStatus());

        String a = tabA.getResponse().getCookie(RefreshCookies.NAME).getValue();
        String b = tabB.getResponse().getCookie(RefreshCookies.NAME).getValue();
        assertNotEquals(a, b);
        assertEquals(200, refresh(a).getResponse().getStatus(), "the family was not revoked");
        assertEquals(200, refresh(b).getResponse().getStatus());
    }

    @Test
    void sixTabsRefreshingWithTheSameCookieAtOnce_allStaySignedIn() throws Exception {
        String original = login(newPatient().email());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return refresh(original).getResponse().getStatus();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) assertEquals(200, f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
    }
}
