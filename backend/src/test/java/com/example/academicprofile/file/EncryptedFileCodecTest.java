package com.example.academicprofile.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.academicprofile.common.exception.BusinessException;

class EncryptedFileCodecTest {
    @TempDir
    Path temp;

    @Test
    void encryptsWithIndependentKeysAndDecryptsOriginalContent() throws Exception {
        byte[] plaintext = "%PDF-1.7 synthetic fixture".getBytes(StandardCharsets.UTF_8);
        EncryptedFileCodec codec = codec(temp.resolve("master.key"));
        Path first = temp.resolve("first.bin");
        Path second = temp.resolve("second.bin");

        EncryptedFileCodec.EncryptionResult firstResult = codec.encrypt(
                new ByteArrayInputStream(plaintext), first, "first.bin");
        EncryptedFileCodec.EncryptionResult secondResult = codec.encrypt(
                new ByteArrayInputStream(plaintext), second, "second.bin");
        FileMetadata firstMetadata = metadata("first.bin", firstResult);

        assertThat(Files.readAllBytes(first)).isNotEqualTo(plaintext);
        assertThat(Files.readAllBytes(second)).isNotEqualTo(Files.readAllBytes(first));
        assertThat(firstResult.wrappedDek()).isNotEqualTo(secondResult.wrappedDek());
        assertThat(codec.decrypt(first, firstMetadata).readAllBytes()).isEqualTo(plaintext);
        assertThat(codec.decryptedSha256(first, firstMetadata))
                .isEqualTo(firstResult.plaintextSha256());
    }

    @Test
    void tamperingAndWrongMasterKeyFailWithoutReturningPlaintext() throws Exception {
        byte[] plaintext = "private attachment".getBytes(StandardCharsets.UTF_8);
        Path ciphertext = temp.resolve("attachment.bin");
        EncryptedFileCodec codec = codec(temp.resolve("correct.key"));
        EncryptedFileCodec.EncryptionResult result = codec.encrypt(
                new ByteArrayInputStream(plaintext), ciphertext, "attachment.bin");
        FileMetadata metadata = metadata("attachment.bin", result);

        byte[] corrupted = Files.readAllBytes(ciphertext);
        corrupted[corrupted.length - 1] ^= 1;
        Files.write(ciphertext, corrupted);
        assertThatThrownBy(() -> codec.decrypt(ciphertext, metadata).readAllBytes())
                .isInstanceOf(Exception.class)
                .hasMessageNotContaining("private attachment");

        Files.delete(ciphertext);
        codec.encrypt(new ByteArrayInputStream(plaintext), ciphertext, "attachment.bin");
        EncryptedFileCodec wrongCodec = codec(temp.resolve("wrong.key"));
        assertThatThrownBy(() -> wrongCodec.decrypt(ciphertext, metadata))
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining(result.wrappedDek());
    }

    @Test
    void existingMasterKeySurvivesRestartAndMissingReplacementCannotDecrypt() throws Exception {
        Path key = temp.resolve("persistent.key");
        EncryptedFileCodec first = codec(key);
        Path ciphertext = temp.resolve("persistent.bin");
        EncryptedFileCodec.EncryptionResult result = first.encrypt(
                new ByteArrayInputStream("restart-safe".getBytes(StandardCharsets.UTF_8)),
                ciphertext,
                "persistent.bin");
        FileMetadata metadata = metadata("persistent.bin", result);

        EncryptedFileCodec restarted = codec(key);
        assertThat(restarted.decrypt(ciphertext, metadata).readAllBytes())
                .isEqualTo("restart-safe".getBytes(StandardCharsets.UTF_8));

        Files.delete(key);
        EncryptedFileCodec replacement = codec(key);
        assertThatThrownBy(() -> replacement.decrypt(ciphertext, metadata))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("restore");
    }

    private EncryptedFileCodec codec(Path key) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setEncryptionKeyFile(key.toString());
        return new EncryptedFileCodec(new FileEncryptionKeyManager(properties));
    }

    private FileMetadata metadata(
            String storedName, EncryptedFileCodec.EncryptionResult result) {
        FileMetadata metadata = new FileMetadata();
        metadata.setStoredName(storedName);
        metadata.setEncryptionVersion(EncryptedFileCodec.VERSION);
        metadata.setEncryptionKeyId(result.keyId());
        metadata.setWrappedDek(result.wrappedDek());
        metadata.setCiphertextStatus("ENCRYPTED");
        metadata.setPlaintextSha256(result.plaintextSha256());
        return metadata;
    }
}
