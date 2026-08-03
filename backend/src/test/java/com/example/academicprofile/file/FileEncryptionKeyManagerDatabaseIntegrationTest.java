package com.example.academicprofile.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.academicprofile.common.exception.BusinessException;

@SpringBootTest
class FileEncryptionKeyManagerDatabaseIntegrationTest {
    @TempDir
    Path temp;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FileMetadataMapper files;

    private Long userId;
    private Long teacherId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO `user`
                    (username, email, password_hash, role, status, auth_version)
                VALUES (?, ?, 'unused-test-hash', 'TEACHER', 'ACTIVE', 1)
                """, "key-state-" + suffix, "key-state-" + suffix + "@example.test");
        userId = jdbc.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, "key-state-" + suffix);
        jdbc.update("INSERT INTO teacher_profile (user_id, display_name) VALUES (?, 'Key State')",
                userId);
        teacherId = jdbc.queryForObject(
                "SELECT id FROM teacher_profile WHERE user_id = ?", Long.class, userId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM file_metadata WHERE teacher_id = ?", teacherId);
        jdbc.update("DELETE FROM teacher_profile WHERE id = ?", teacherId);
        jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
    }

    @Test
    void realEncryptedRowPreventsMissingKeyReplacement() {
        insertEncrypted("existing-key-id");
        Path missing = temp.resolve("missing.key");

        assertThatThrownBy(() -> new FileEncryptionKeyManager(properties(missing), files))
                .isInstanceOf(BusinessException.class);
        assertThat(missing).doesNotExist();
    }

    @Test
    void realEncryptedRowAcceptsOnlyMatchingRestoredKey() throws Exception {
        Path restored = temp.resolve("restored.key");
        FileEncryptionKeyManager initial = new FileEncryptionKeyManager(properties(restored));
        insertEncrypted(initial.keyId());

        assertThat(new FileEncryptionKeyManager(properties(restored), files).keyId())
                .isEqualTo(initial.keyId());

        Path wrong = temp.resolve("wrong.key");
        Files.write(wrong, new byte[32]);
        assertThatThrownBy(() -> new FileEncryptionKeyManager(properties(wrong), files))
                .isInstanceOf(BusinessException.class);
    }

    private void insertEncrypted(String keyId) {
        jdbc.update("""
                INSERT INTO file_metadata
                    (teacher_id, original_name, stored_name, storage_path, file_ext,
                     mime_type, file_size, business_type, encryption_version,
                     encryption_key_id, wrapped_dek, ciphertext_status,
                     plaintext_sha256, deleted)
                VALUES (?, 'synthetic.pdf', 'synthetic.bin', 'isolated/synthetic.bin',
                        'pdf', 'application/pdf', 1, 'PAPER', 'tink-streaming-aead-v1',
                        ?, 'synthetic-wrapped-value', 'ENCRYPTED',
                        '0000000000000000000000000000000000000000000000000000000000000000', 0)
                """, teacherId, keyId);
    }

    private FileStorageProperties properties(Path key) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setEncryptionKeyFile(key.toString());
        return properties;
    }
}
