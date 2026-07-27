package com.example.academicprofile.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.academicprofile.common.exception.BusinessException;

import jakarta.annotation.PostConstruct;

@Component
public class AiApiKeyCipher {

    private static final String VERSION = "v1";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String DECRYPTION_ERROR = "AI 配置无法解密，请恢复加密密钥备份";

    private final Path keyFile;
    private final SecureRandom secureRandom;
    private volatile SecretKey secretKey;
    private volatile String keyId;

    @Autowired
    public AiApiKeyCipher(AiProperties properties) {
        this(Path.of(properties.getEncryptionKeyFile()), new SecureRandom());
    }

    AiApiKeyCipher(byte[] keyBytes) {
        this.keyFile = null;
        this.secureRandom = new SecureRandom();
        setKey(keyBytes);
    }

    private AiApiKeyCipher(Path keyFile, SecureRandom secureRandom) {
        this.keyFile = keyFile;
        this.secureRandom = secureRandom;
    }

    @PostConstruct
    void initialize() {
        if (secretKey != null) {
            return;
        }
        try {
            Path parent = keyFile.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(keyFile)) {
                createKeyFileAtomically(parent);
            }
            byte[] keyBytes = Files.readAllBytes(keyFile);
            setKey(keyBytes);
            restrictPermissions(keyFile);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Unable to initialize AI configuration encryption");
        }
    }

    public EncryptedValue encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String encoded = String.join(
                    ":",
                    VERSION,
                    keyId,
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext));
            return new EncryptedValue(encoded, keyId);
        } catch (GeneralSecurityException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 配置加密失败");
        }
    }

    public String decrypt(String encoded, String expectedKeyId) {
        try {
            String[] parts = encoded == null ? new String[0] : encoded.split(":", -1);
            if (parts.length != 4
                    || !VERSION.equals(parts[0])
                    || !keyId.equals(parts[1])
                    || !keyId.equals(expectedKeyId)) {
                throw decryptionException();
            }
            byte[] iv = Base64.getDecoder().decode(parts[2]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[3]);
            if (iv.length != IV_BYTES) {
                throw decryptionException();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw decryptionException();
        }
    }

    String keyId() {
        return keyId;
    }

    private void createKeyFileAtomically(Path parent) throws IOException {
        byte[] generated = new byte[KEY_BYTES];
        secureRandom.nextBytes(generated);
        Path temporary = Files.createTempFile(parent, ".ai-config-", ".key.tmp");
        try {
            Files.write(temporary, generated);
            restrictPermissions(temporary);
            try {
                // A hard link publishes the fully-written temporary file with CREATE_NEW
                // semantics, so concurrent starters cannot replace each other's key.
                Files.createLink(keyFile, temporary);
            } catch (java.nio.file.FileAlreadyExistsException ex) {
                // Another application instance won the create race; use its completed file.
            } catch (UnsupportedOperationException | IOException ex) {
                try {
                    Files.copy(temporary, keyFile);
                } catch (java.nio.file.FileAlreadyExistsException race) {
                    // Another application instance won the create race.
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void setKey(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("AI configuration encryption key must contain exactly 32 bytes");
        }
        byte[] copy = keyBytes.clone();
        secretKey = new SecretKeySpec(copy, "AES");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(copy);
            keyId = HexFormat.of().formatHex(digest, 0, 8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to identify AI configuration encryption key", ex);
        }
    }

    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // POSIX permissions are not available on all supported development hosts.
        }
    }

    private BusinessException decryptionException() {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, DECRYPTION_ERROR);
    }

    public record EncryptedValue(String ciphertext, String keyId) {
    }
}
