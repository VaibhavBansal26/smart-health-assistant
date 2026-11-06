package com.healthcare.app.recommendation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Safety-critical rules, so they get their own thorough, fast tests.
 * Rule of thumb encoded here: a false alarm is acceptable, a missed emergency is not.
 */
@DisplayName("Emergency red-flag rules")
class RedFlagDetectorTest {

    private final RedFlagDetector detector = new RedFlagDetector();

    @ParameterizedTest(name = "flags: \"{0}\" as {1}")
    @CsvSource(delimiter = '|', value = {
            "I have chest pain and shortness of breath|Possible heart attack",
            "pressure in my chest, my left arm is numb|Possible heart attack",
            "Chest Tightness with cold sweat|Possible heart attack",
            "squeezing feeling in the chest and I feel nauseous|Possible heart attack",
            "my speech is slurred speech and face droops|Possible stroke",
            "sudden weakness on one side|Possible stroke",
            "worst headache of my life|Possible stroke",
            "I want to kill myself|Thoughts of self-harm",
            "I have been thinking about suicide|Thoughts of self-harm",
            "I just want to die|Thoughts of self-harm",
            "I can't breathe|Severe breathing difficulty",
            "gasping for air|Severe breathing difficulty",
            "my lips are turning blue|Severe breathing difficulty",
            "I am vomiting blood|Severe bleeding",
            "coughing up blood|Severe bleeding",
            "bleeding that won't stop|Severe bleeding",
            "he is unconscious|Loss of consciousness",
            "my face is swelling and I can't swallow|Possible severe allergic reaction",
            "chest pain and I feel dizzy|Possible heart attack",
            "pain in my chest and I am sweaty and nauseated|Possible heart attack",
            "chest tightness, feeling lightheaded|Possible heart attack",
            "chest discomfort spreading to my jaw|Possible heart attack",
            "my chest hurts and I feel faint|Possible heart attack",
            "tightness across my chest and I am sweating|Possible heart attack",
            "my chest feels heavy and I am short of breath|Possible heart attack",
    })
    void detectsEmergencies(String text, String category) {
        var flag = detector.detect(text);
        assertTrue(flag.isPresent(), "should flag: " + text);
        assertEquals(category, flag.get().category());
        assertFalse(flag.get().message().isBlank());
    }

    @ParameterizedTest(name = "does not flag: \"{0}\"")
    @ValueSource(strings = {
            "I have a mild headache",
            "chest pain after lifting weights",             // chest pain alone: handled by the normal flow
            "my chest hurts when I cough",
            "pain in my chest when I press on it",
            "short of breath after running",                // breathlessness alone
            "my throat is swollen and sore",                // swelling without breathing/swallowing wording
            "dry cough for two weeks",
            "itchy rash on my arms",
            "I feel sad and tired",
            "my knee hurts when I climb stairs",
    })
    void doesNotCryWolfOnOrdinaryComplaints(String text) {
        assertTrue(detector.detect(text).isEmpty(), "should not flag: " + text);
    }

    @Test
    void combinesSymptomsMentionedInSeparateMessages() {
        // the chat joins the last few user messages with a space
        assertTrue(detector.detect("I have chest pain an hour ago, and I am short of breath").isPresent());
    }

    @Test
    void isCaseInsensitive() {
        assertTrue(detector.detect("CHEST PAIN AND SHORTNESS OF BREATH").isPresent());
    }

    @Test
    void selfHarmMessage_pointsToHelp_withoutInventingPhoneNumbers() {
        String message = detector.detect("I want to end my life").orElseThrow().message();
        assertTrue(message.toLowerCase().contains("crisis") || message.toLowerCase().contains("emergency number"));
        assertFalse(message.matches(".*\\d{3,}.*"), "no hard-coded phone numbers (they vary by country)");
    }
}
