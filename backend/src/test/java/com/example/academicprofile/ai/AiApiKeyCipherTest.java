package com.example.academicprofile.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.common.exception.BusinessException;

class AiApiKeyCipherTest {

    private static final String API_KEY = "unit-test-secret-value-1234";

    @Test
    void encryptionUsesRandomIvAndRoundTripsWithoutPlaintext() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(key((byte) 1));

        AiApiKeyCipher.EncryptedValue first = cipher.encrypt(API_KEY);
        AiApiKeyCipher.EncryptedValue second = cipher.encrypt(API_KEY);

        assertFalse(first.ciphertext().contains(API_KEY));
        assertNotEquals(first.ciphertext(), second.ciphertext());
        assertEquals(API_KEY, cipher.decrypt(first.ciphertext(), first.keyId()));
        assertEquals(API_KEY, cipher.decrypt(second.ciphertext(), second.keyId()));
    }

    @Test
    void tamperingFailsWithSafeMessage() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(key((byte) 2));
        AiApiKeyCipher.EncryptedValue encrypted = cipher.encrypt(API_KEY);
        String tampered = encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 2) + "AA";

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cipher.decrypt(tampered, encrypted.keyId()));

        assertEquals("AI 配置无法解密，请恢复加密密钥备份", exception.getMessage());
        assertFalse(exception.getMessage().contains(API_KEY));
        assertFalse(exception.getMessage().contains(encrypted.ciphertext()));
    }

    @Test
    void wrongMasterKeyOrKeyIdFailsSafely() {
        AiApiKeyCipher original = new AiApiKeyCipher(key((byte) 3));
        AiApiKeyCipher.EncryptedValue encrypted = original.encrypt(API_KEY);
        AiApiKeyCipher wrong = new AiApiKeyCipher(key((byte) 4));

        assertThrows(BusinessException.class, () -> wrong.decrypt(encrypted.ciphertext(), encrypted.keyId()));
        assertThrows(BusinessException.class, () -> original.decrypt(encrypted.ciphertext(), "wrong-key-id"));
    }

    private byte[] key(byte marker) {
        byte[] key = new byte[32];
        Arrays.fill(key, marker);
        byte[] suffix = "academic-profile-test".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(suffix, 0, key, 0, Math.min(suffix.length, key.length));
        key[key.length - 1] = marker;
        return key;
    }
}
