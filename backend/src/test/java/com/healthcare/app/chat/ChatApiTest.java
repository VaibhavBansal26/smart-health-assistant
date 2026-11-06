package com.healthcare.app.chat;

import com.healthcare.app.recommendation.MlClient;
import com.healthcare.app.support.ApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs in demo mode (scripted replies), so no network or API key is involved. */
@DisplayName("Chat API (streaming)")
class ChatApiTest extends ApiTest {

    private Map<String, Object> turn(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private String streamBody(String token, List<Map<String, Object>> messages) throws Exception {
        return post("/api/chat/stream", token, Map.of("messages", messages))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void status_reportsDemoMode() throws Exception {
        get("/api/chat/status", newPatient().token())
                .andExpect(jsonPath("$.provider").value("demo"));
    }

    @Test
    void reply_isStreamedAsServerSentEvents_endingWithDone() throws Exception {
        when(ml.predict(any())).thenReturn(new MlClient.Prediction(List.of(new MlClient.Item("Dermatology", 0.9)), false));

        String stream = streamBody(newPatient().token(), List.of(turn("user", "I have an itchy rash")));

        assertThat(stream, containsString("event: delta\ndata: {\"text\":"));
        assertThat(stream, containsString("Dermatology")); // the demo reply used the ML hint
        assertThat(stream, endsWith("event: done\ndata: {}\n\n"));
        assertThat(stream, not(containsString("event: emergency")));
    }

    @Test
    void emergency_isDetectedAcrossMessages_andTheModelIsNeverConsulted() throws Exception {
        String stream = streamBody(newPatient().token(), List.of(
                turn("user", "I have chest pain"),
                turn("assistant", "How long has it lasted?"),
                turn("user", "an hour, and I am short of breath")));

        assertThat(stream, startsWith("event: emergency\ndata: {\"message\":"));
        assertThat(stream, not(containsString("event: delta")));
        verifyNoInteractions(ml);
    }

    @Test
    void conversationShape_isValidated() throws Exception {
        String token = newPatient().token();
        post("/api/chat/stream", token, Map.of("messages", List.of())).andExpect(status().isBadRequest());
        post("/api/chat/stream", token, Map.of("messages", List.of(turn("system", "hi"))))
                .andExpect(status().isBadRequest());
        post("/api/chat/stream", token, Map.of("messages", List.of(turn("user", "x".repeat(2001)))))
                .andExpect(status().isBadRequest());
        post("/api/chat/stream", token, Map.of("messages", List.of(turn("user", "hi"), turn("assistant", "hello"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The last message must come from the user."));
    }

    @Test
    void demoReply_stillWorks_whenTheMlServiceReturnsNothingUsable() throws Exception {
        // an unstubbed mock returns null, like a broken ML service would
        String stream = streamBody(newPatient().token(), List.of(turn("user", "I have a headache")));
        assertThat(stream, containsString("event: delta"));
        assertThat(stream, endsWith("event: done\ndata: {}\n\n"));
        assertThat(stream, not(containsString("event: error")));
    }

    @Test
    void aLeadingAssistantMessage_isDroppedRatherThanFailing() throws Exception {
        String stream = streamBody(newPatient().token(), List.of(turn("assistant", "Hello!"), turn("user", "I have a headache")));
        assertThat(stream, endsWith("event: done\ndata: {}\n\n"));
    }

    @Test
    void rateLimit_stopsRunawayUsage_perUser() throws Exception {
        // test profile allows 5 per minute; use emergency text so each call is instant
        var spammer = newPatient();
        var bystander = newPatient();
        for (int i = 0; i < 5; i++) {
            streamBody(spammer.token(), List.of(turn("user", "chest pain and short of breath")));
        }
        post("/api/chat/stream", spammer.token(), Map.of("messages", List.of(turn("user", "hello there"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("too quickly")));
        // another user is unaffected
        streamBody(bystander.token(), List.of(turn("user", "chest pain and short of breath")));
    }

    @Test
    void accessRules() throws Exception {
        String doctor = seededDoctor("asha.verma@clinic.test").token();
        post("/api/chat/stream", doctor, Map.of("messages", List.of(turn("user", "hi")))).andExpect(status().isForbidden());
        post("/api/chat/stream", null, Map.of("messages", List.of(turn("user", "hi")))).andExpect(status().isUnauthorized());
        get("/api/chat/status", null).andExpect(status().isUnauthorized());
    }

    private static void assertThat(String actual, org.hamcrest.Matcher<String> matcher) {
        org.hamcrest.MatcherAssert.assertThat(actual, matcher);
    }
}
