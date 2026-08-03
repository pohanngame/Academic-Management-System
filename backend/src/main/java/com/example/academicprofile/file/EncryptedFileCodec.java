package com.example.academicprofile.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.academicprofile.common.exception.BusinessException;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;

@Component
public class EncryptedFileCodec {
    public static final String VERSION = "TINK_STREAMING_AEAD_V1";
    private static final byte[] WRAP_AAD = "academic-profile:file-dek:v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private final FileEncryptionKeyManager masterKey;

    public EncryptedFileCodec(FileEncryptionKeyManager masterKey) {
        this.masterKey = masterKey;
        try {
            StreamingAeadConfig.register();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Tink Streaming AEAD initialization failed", ex);
        }
    }

    public EncryptionResult encrypt(InputStream plaintext, Path ciphertext, String associatedName) {
        try {
            KeysetHandle handle = KeysetHandle.generateNew(
                    PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB);
            StreamingAead streaming = handle.getPrimitive(StreamingAead.class);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(plaintext, digest);
                    OutputStream raw = Files.newOutputStream(
                            ciphertext, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    OutputStream encrypted = streaming.newEncryptingStream(raw, aad(associatedName))) {
                input.transferTo(encrypted);
            }
            byte[] serialized = TinkProtoKeysetFormat.serializeKeyset(
                    handle, InsecureSecretKeyAccess.get());
            return new EncryptionResult(
                    wrap(serialized),
                    masterKey.keyId(),
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | GeneralSecurityException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to encrypt attachment");
        }
    }

    public InputStream decrypt(Path ciphertext, FileMetadata metadata) {
        if (!VERSION.equals(metadata.getEncryptionVersion())
                || !"ENCRYPTED".equals(metadata.getCiphertextStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Attachment has not completed secure migration");
        }
        try {
            byte[] serialized = unwrap(metadata.getWrappedDek(), metadata.getEncryptionKeyId());
            KeysetHandle handle = TinkProtoKeysetFormat.parseKeyset(
                    serialized, InsecureSecretKeyAccess.get());
            StreamingAead streaming = handle.getPrimitive(StreamingAead.class);
            return streaming.newDecryptingStream(
                    Files.newInputStream(ciphertext), aad(metadata.getStoredName()));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException ex) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Attachment cannot be decrypted; restore the encryption key backup");
        }
    }

    public String decryptedSha256(Path ciphertext, FileMetadata metadata) {
        try (InputStream input = decrypt(ciphertext, metadata)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Attachment migration verification failed");
        }
    }

    private String wrap(byte[] serialized) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey.key(), "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(WRAP_AAD);
        byte[] encrypted = cipher.doFinal(serialized);
        return "v1:" + masterKey.keyId() + ":"
                + Base64.getEncoder().encodeToString(iv) + ":"
                + Base64.getEncoder().encodeToString(encrypted);
    }

    private byte[] unwrap(String wrapped, String expectedKeyId) throws GeneralSecurityException {
        if (wrapped == null) {
            throw new GeneralSecurityException("missing wrapped key");
        }
        String[] parts = wrapped.split(":", -1);
        if (parts.length != 4 || !"v1".equals(parts[0])
                || !masterKey.keyId().equals(parts[1])
                || !masterKey.keyId().equals(expectedKeyId)) {
            throw new GeneralSecurityException("key mismatch");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey.key(), "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[2])));
            cipher.updateAAD(WRAP_AAD);
            return cipher.doFinal(Base64.getDecoder().decode(parts[3]));
        } catch (AEADBadTagException ex) {
            throw new GeneralSecurityException("wrapped key authentication failed");
        }
    }

    private byte[] aad(String associatedName) {
        return ("academic-profile:attachment:" + associatedName).getBytes(StandardCharsets.UTF_8);
    }

    public record EncryptionResult(String wrappedDek, String keyId, String plaintextSha256) {
    }
}
