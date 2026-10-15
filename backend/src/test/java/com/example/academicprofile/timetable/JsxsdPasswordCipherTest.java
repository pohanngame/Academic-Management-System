package com.example.academicprofile.timetable;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class JsxsdPasswordCipherTest {

    private static final String SALT = "0123456789abcdef";

    /** 认证页字符集；不能含 I、L、O、l、o、0、1、9。 */
    private static final String ALLOWED = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
    private static final String FORBIDDEN = "ILOl o019".replace(" ", "");

    /** 从密文里解出真正的明文（前缀 + 原密码）。 */
    private static String decrypt(JsxsdPasswordCipher.EncryptedPassword encrypted, String salt)
            throws Exception {
        Cipher decoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decoder.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(encrypted.iv().getBytes(StandardCharsets.UTF_8)));
        return new String(
                decoder.doFinal(Base64.getDecoder().decode(encrypted.ciphertext())),
                StandardCharsets.UTF_8);
    }

    @Test
    void producesDecryptableCiphertextWithSixtyFourCharPrefix() throws Exception {
        JsxsdPasswordCipher cipher = new JsxsdPasswordCipher(new SecureRandom());

        JsxsdPasswordCipher.EncryptedPassword encrypted = cipher.encrypt("secret-pass", SALT);

        assertThat(encrypted.iv()).hasSize(16);
        // 用页面规则反向解开，确认前缀长度与明文一致。
        Cipher decoder = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decoder.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(SALT.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(encrypted.iv().getBytes(StandardCharsets.UTF_8)));
        String plain = new String(
                decoder.doFinal(Base64.getDecoder().decode(encrypted.ciphertext())),
                StandardCharsets.UTF_8);

        assertThat(plain).hasSize(64 + "secret-pass".length());
        assertThat(plain).endsWith("secret-pass");
    }

    /**
     * 前缀与向量只能使用认证页字符集，且绝不能出现 I、L、O、l、o、0、1、9。
     * 多跑几轮以降低偶然通过的概率。
     */
    @Test
    void prefixAndIvOnlyUseTheAcademicAffairsAlphabet() throws Exception {
        JsxsdPasswordCipher cipher = new JsxsdPasswordCipher(new SecureRandom());

        for (int round = 0; round < 200; round++) {
            JsxsdPasswordCipher.EncryptedPassword encrypted = cipher.encrypt("p", SALT);
            String plain = decrypt(encrypted, SALT);
            String prefix = plain.substring(0, 64);

            assertThat(encrypted.iv()).hasSize(16);
            for (char ch : prefix.toCharArray()) {
                assertThat(ALLOWED.indexOf(ch))
                        .as("prefix char %s must be in the alphabet", ch)
                        .isGreaterThanOrEqualTo(0);
            }
            for (char ch : encrypted.iv().toCharArray()) {
                assertThat(ALLOWED.indexOf(ch))
                        .as("iv char %s must be in the alphabet", ch)
                        .isGreaterThanOrEqualTo(0);
            }
            for (char ch : FORBIDDEN.toCharArray()) {
                assertThat(prefix).doesNotContain(String.valueOf(ch));
                assertThat(encrypted.iv()).doesNotContain(String.valueOf(ch));
            }
        }
    }

    /**
     * 固定前缀、向量与盐时，解密结果必须正好是“前缀 + 原密码”。
     * 用一个只吐固定字节的 SecureRandom 子类，把随机源变成确定值。
     */
    @Test
    void decryptsToPrefixPlusPlainPasswordWithFixedRandomness() throws Exception {
        // nextInt(bound) 固定返回 7 => 前缀与向量全为 ALLOWED[7] = 'H'。
        SecureRandom fixed = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 7;
            }
        };
        JsxsdPasswordCipher cipher = new JsxsdPasswordCipher(fixed);

        JsxsdPasswordCipher.EncryptedPassword encrypted = cipher.encrypt("Passw0rd!", SALT);

        String expectedPrefix = "H".repeat(64);
        assertThat(encrypted.iv()).isEqualTo("H".repeat(16));
        assertThat(decrypt(encrypted, SALT)).isEqualTo(expectedPrefix + "Passw0rd!");
    }

    @Test
    void usesFreshIvAndPrefixPerCall() {
        JsxsdPasswordCipher cipher = new JsxsdPasswordCipher(new SecureRandom());

        JsxsdPasswordCipher.EncryptedPassword first = cipher.encrypt("same", SALT);
        JsxsdPasswordCipher.EncryptedPassword second = cipher.encrypt("same", SALT);

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void rejectsMissingSaltWithoutLeakingInput() {
        JsxsdPasswordCipher cipher = new JsxsdPasswordCipher(new SecureRandom());

        assertThat(org.assertj.core.api.Assertions
                .catchThrowable(() -> cipher.encrypt("secret-pass", "")))
                .isInstanceOf(com.example.academicprofile.common.exception.BusinessException.class)
                .hasMessageNotContaining("secret-pass");
    }
}
