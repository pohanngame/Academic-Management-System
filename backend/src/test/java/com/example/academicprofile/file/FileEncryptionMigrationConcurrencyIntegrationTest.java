package com.example.academicprofile.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class FileEncryptionMigrationConcurrencyIntegrationTest {
    @TempDir
    Path temp;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FileMetadataMapper files;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long userId;
    private Long teacherId;
    private Long fileId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO `user`
                    (username, email, password_hash, role, status, auth_version)
                VALUES (?, ?, 'unused-test-hash', 'TEACHER', 'ACTIVE', 1)
                """, "migration-" + suffix, "migration-" + suffix + "@example.test");
        userId = jdbc.queryForObject(
                "SELECT id FROM `user` WHERE username = ?", Long.class, "migration-" + suffix);
        jdbc.update("INSERT INTO teacher_profile (user_id, display_name) VALUES (?, 'Migration')",
                userId);
        teacherId = jdbc.queryForObject(
                "SELECT id FROM teacher_profile WHERE user_id = ?", Long.class, userId);

        Path legacy = temp.resolve("uploads/legacy/source.pdf");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "%PDF concurrent synthetic", StandardCharsets.UTF_8);
        jdbc.update("""
                INSERT INTO file_metadata
                    (teacher_id, original_name, stored_name, storage_path, file_ext,
                     mime_type, file_size, business_type, ciphertext_status, deleted)
                VALUES (?, 'source.pdf', 'source.pdf', 'legacy/source.pdf', 'pdf',
                        'application/pdf', 25, 'PAPER', 'PLAINTEXT', 0)
                """, teacherId);
        fileId = jdbc.queryForObject(
                "SELECT id FROM file_metadata WHERE teacher_id = ?", Long.class, teacherId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM file_metadata WHERE teacher_id = ?", teacherId);
        jdbc.update("DELETE FROM teacher_profile WHERE id = ?", teacherId);
        jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
    }

    @Test
    void twoMigrationWorkersPublishOnlyOneCiphertext() throws Exception {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setUploadDir(temp.resolve("uploads").toString());
        properties.setEncryptionKeyFile(temp.resolve("secrets/file-encryption.key").toString());
        EncryptedFileCodec codec = new EncryptedFileCodec(new FileEncryptionKeyManager(properties));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        FileEncryptionMigrationService first =
                new FileEncryptionMigrationService(files, properties, codec, transactions);
        FileEncryptionMigrationService second =
                new FileEncryptionMigrationService(files, properties, codec, transactions);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> migrateWhenReleased(first, ready, start));
            var secondResult = executor.submit(() -> migrateWhenReleased(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int migrated = firstResult.get(10, TimeUnit.SECONDS).migrated()
                    + secondResult.get(10, TimeUnit.SECONDS).migrated();
            assertThat(migrated).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        FileMetadata stored = files.selectById(fileId);
        assertThat(stored.getCiphertextStatus()).isEqualTo("ENCRYPTED");
        assertThat(stored.getLegacyStoragePath()).isNull();
        Path ciphertext = temp.resolve("uploads").resolve(stored.getStoragePath());
        assertThat(ciphertext).isRegularFile();
        assertThat(codec.decrypt(ciphertext, stored).readAllBytes())
                .isEqualTo("%PDF concurrent synthetic".getBytes(StandardCharsets.UTF_8));
        try (var paths = Files.walk(temp.resolve("uploads"))) {
            assertThat(paths.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
    }

    private FileEncryptionMigrationService.MigrationResult migrateWhenReleased(
            FileEncryptionMigrationService service,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent migration did not start");
        }
        return service.migrateAll();
    }
}
