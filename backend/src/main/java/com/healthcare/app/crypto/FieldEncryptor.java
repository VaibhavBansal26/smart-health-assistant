package com.healthcare.app.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Application-level encryption of sensitive columns (appointment reasons, risk-check measurements).
 *
 * AES-256-GCM: confidentiality AND tamper detection. A fresh random 96-bit nonce per value means the same text never
 * encrypts to the same bytes. Stored format:  enc:v1:&lt;keyId&gt;:&lt;base64url(nonce || ciphertext || tag)&gt;
 *
 * Key rotation: put the new key in DATA_ENCRYPTION_KEY and the old one in DATA_ENCRYPTION_KEY_PREVIOUS. New writes use
 * the new key, old rows stay readable (the key id says which key to use) and migrate as they are updated.
 * Values without the "enc:" prefix are treated as legacy plaintext and returned as-is.
 *
 * Losing the key means losing the data. Keep it in a secrets manager, separate from the database and its backups.
 */
@Component
public class FieldEncryptor {

    private static final String PREFIX = "enc:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey current;
    private final String currentKeyId;
    private final Map<String, SecretKey> keysById = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(@Value("${app.security.data-encryption-key:}") String key,
                          @Value("${app.security.data-encryption-key-previous:}") String previousKey) {
        this.current = parse(key, "DATA_ENCRYPTION_KEY");
        this.currentKeyId = keyId(current);
        keysById.put(currentKeyId, current);
        if (previousKey != null && !previousKey.isBlank()) {
            SecretKey previous = parse(previousKey, "DATA_ENCRYPTION_KEY_PREVIOUS");
            keysById.put(keyId(previous), previous);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(currentKeyId.getBytes(StandardCharsets.UTF_8)); // binds the ciphertext to its key id
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[NONCE_BYTES + ciphertext.length];
            System.arraycopy(nonce, 0, packed, 0, NONCE_BYTES);
            System.arraycopy(ciphertext, 0, packed, NONCE_BYTES, ciphertext.length);
            return PREFIX + currentKeyId + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // null, or legacy plaintext written before encryption was enabled
        }
        String[] parts = stored.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Malformed encrypted value");
        }
        SecretKey key = keysById.get(parts[0]);
        if (key == null) {
            throw new IllegalStateException("Value was encrypted with an unknown key (" + parts[0]
                    + "). Configure it as DATA_ENCRYPTION_KEY_PREVIOUS.");
        }
        try {
            byte[] packed = Base64.getUrlDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, packed, 0, NONCE_BYTES));
            cipher.updateAAD(parts[0].getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(packed, NONCE_BYTES, packed.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (AEADBadTagException tampered) {
            throw new IllegalStateException("Encrypted value failed authentication (tampered with, or wrong key)", tampered);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt value", e);
        }
    }

    private static SecretKey parse(String base64, String name) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
            if (bytes.length == 32) {
                return new SecretKeySpec(bytes, "AES");
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to the error below
        }
        throw new IllegalStateException(name + " is missing or invalid: it must be 32 random bytes encoded as base64 "
                + "(generate one with `openssl rand -base64 32`). See .env.example.");
    }

    private static String keyId(SecretKey key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()), 0, 4);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
