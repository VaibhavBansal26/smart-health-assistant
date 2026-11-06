package com.healthcare.app.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Field encryption (AES-256-GCM)")
class FieldEncryptorTest {

    private static String newKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    private final String keyA = newKey();
    private final FieldEncryptor enc = new FieldEncryptor(keyA, "");

    @Test
    void roundTrips_includingUnicodeEmptyAndLongText() {
        for (String text : new String[]{"hello", "", "chest pain after climbing stairs", "naïve café 症状 🩺", "x".repeat(5000)}) {
            assertEquals(text, enc.decrypt(enc.encrypt(text)));
        }
    }

    @Test
    void ciphertext_looksNothingLikeThePlaintext_andIsVersioned() {
        String out = enc.encrypt("my secret symptom");
        assertTrue(out.startsWith("enc:v1:"));
        assertFalse(out.contains("secret"));
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime_becauseOfARandomNonce() {
        assertNotEquals(enc.encrypt("same text"), enc.encrypt("same text"));
    }

    @Test
    void tampering_isDetectedNotSilentlyAccepted() {
        String out = enc.encrypt("do not alter me");
        String flipped = out.substring(0, out.length() - 3) + (out.endsWith("AAA") ? "BBB" : "AAA");
        assertThrows(IllegalStateException.class, () -> enc.decrypt(flipped));
        assertThrows(IllegalStateException.class, () -> enc.decrypt("enc:v1:garbage"));
        assertThrows(IllegalStateException.class, () -> enc.decrypt("enc:v1:00000000:AAAA"));
    }

    @Test
    void aDifferentKeyCannotReadTheData() {
        String out = enc.encrypt("private");
        assertThrows(IllegalStateException.class, () -> new FieldEncryptor(newKey(), "").decrypt(out));
    }

    @Test
    void legacyPlaintextAndNulls_passThroughUnchanged() {
        assertEquals("written before encryption existed", enc.decrypt("written before encryption existed"));
        assertNull(enc.encrypt(null));
        assertNull(enc.decrypt(null));
    }

    @Test
    void keyRotation_newKeyForWrites_oldKeyStillReadsOldRows() {
        String old = enc.encrypt("written under the old key");
        String newKey = newKey();
        FieldEncryptor rotated = new FieldEncryptor(newKey, keyA);

        assertEquals("written under the old key", rotated.decrypt(old));
        String fresh = rotated.encrypt("written under the new key");
        assertNotEquals(old.split(":")[2], fresh.split(":")[2], "new writes carry the new key id");
        assertEquals("written under the new key", rotated.decrypt(fresh));
        assertThrows(IllegalStateException.class, () -> enc.decrypt(fresh)); // old config cannot read new rows
    }

    @Test
    void refusesToStart_withAMissingOrBadKey() {
        for (String bad : new String[]{"", "   ", "not base64 !!", Base64.getEncoder().encodeToString(new byte[16]),
                Base64.getEncoder().encodeToString(new byte[33])}) {
            var e = assertThrows(IllegalStateException.class, () -> new FieldEncryptor(bad, ""), "key: '" + bad + "'");
            assertTrue(e.getMessage().contains("DATA_ENCRYPTION_KEY"));
        }
        assertThrows(IllegalStateException.class, () -> new FieldEncryptor(null, ""));
        assertThrows(IllegalStateException.class, () -> new FieldEncryptor(keyA, "bad-previous-key"));
    }
}
