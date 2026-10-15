package com.example.academicprofile.timetable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.academicprofile.common.exception.BusinessException;

/**
 * 复刻教务认证页的密码加密规则：
 * 明文前拼 64 位随机前缀，AES/CBC/PKCS7，密钥为页面 pwdEncryptSalt，向量为 16 位随机串。
 * 只返回密文与向量，调用方用完即弃，绝不落库或写日志。
 */
@Component
public class JsxsdPasswordCipher {

    /**
     * 认证页使用的字符集：不含 I、L、O、l、o、0、1、9，
     * 避免与数字 1/0 混淆而让教务端校验失败。前缀与向量都只能用这个集合。
     */
    private static final String ALPHABET =
            "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
    private static final int PREFIX_LENGTH = 64;
    private static final int IV_LENGTH = 16;

    private final SecureRandom secureRandom;

    public JsxsdPasswordCipher() {
        this(new SecureRandom());
    }

    JsxsdPasswordCipher(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public EncryptedPassword encrypt(String password, String pwdEncryptSalt) {
        if (password == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Password must not be null");
        }
        if (pwdEncryptSalt == null || pwdEncryptSalt.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Academic affairs login page did not provide pwdEncryptSalt");
        }
        String prefixed = randomString(PREFIX_LENGTH) + password;
        String iv = randomString(IV_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(
                    pwdEncryptSalt.getBytes(StandardCharsets.UTF_8), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key,
                    new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
            byte[] encrypted = cipher.doFinal(prefixed.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPassword(Base64.getEncoder().encodeToString(encrypted), iv);
        } catch (GeneralSecurityException ex) {
            // 不回显密钥或明文，避免把教务凭据带进日志。
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to encrypt the academic affairs password");
        }
    }

    private String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    /** PKCS7 在 JDK 中即 PKCS5Padding（两者对 AES 等价）。 */
    public record EncryptedPassword(String ciphertext, String iv) {
    }
}
