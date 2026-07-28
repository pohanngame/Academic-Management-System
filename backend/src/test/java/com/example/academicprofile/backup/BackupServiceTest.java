package com.example.academicprofile.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.academicprofile.ai.AiProperties;
import com.example.academicprofile.ai.AiProviderConfigMapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileStorageProperties;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class BackupServiceTest {

    @TempDir
    Path temp;

    private Path uploads;
    private Path key;
    private DatabaseBackupSource database;
    private AiProviderConfigMapper aiConfigs;
    private SystemMaintenanceLock lock;
    private BackupProperties backup;
    private BackupService service;

    @BeforeEach
    void setUp() throws Exception {
        uploads = Files.createDirectories(temp.resolve("uploads"));
        key = Files.createDirectories(temp.resolve("secrets")).resolve("ai-config.key");
        Files.writeString(key, "test-master-key-material", StandardCharsets.UTF_8);
        Files.writeString(uploads.resolve("attachment.txt"), "private attachment", StandardCharsets.UTF_8);

        backup = new BackupProperties();
        backup.setMinimumFreeBytes(0);
        backup.setApplicationVersion("test");
        backup.setGitCommit("abc123");
        FileStorageProperties fileProperties = new FileStorageProperties();
        fileProperties.setUploadDir(uploads.toString());
        fileProperties.setTempDir(temp.resolve("temp").toString());
        AiProperties ai = new AiProperties();
        ai.setEncryptionKeyFile(key.toString());

        database = org.mockito.Mockito.mock(DatabaseBackupSource.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(0), "-- deterministic test dump");
            return null;
        }).when(database).dump(any(Path.class), any(Path.class));
        when(database.flywayVersion()).thenReturn("11");
        when(database.databaseVersion()).thenReturn("8.4-test");

        UserAccountService users = org.mockito.Mockito.mock(UserAccountService.class);
        UserAccount admin = new UserAccount();
        admin.setId(1L);
        admin.setPasswordHash("bcrypt");
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        PasswordEncoder passwords = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(passwords.matches("current-password", "bcrypt")).thenReturn(true);
        aiConfigs = org.mockito.Mockito.mock(AiProviderConfigMapper.class);
        lock = new SystemMaintenanceLock();
        service = new BackupService(
                backup, fileProperties, ai, database,
                new ObjectMapper().findAndRegisterModules(), users, passwords, aiConfigs, lock);
    }

    @Test
    void createsHashedPackageWithDatabaseUploadsAndEncryptionKey() throws Exception {
        BackupArchive archive = service.create(operator(), new BackupPasswordRequest("current-password"));

        try (ZipFile zip = new ZipFile(archive.path().toFile())) {
            assertThat(zip.getEntry("manifest.json")).isNotNull();
            assertThat(zip.getEntry("database.sql")).isNotNull();
            assertThat(zip.getEntry("uploads/attachment.txt")).isNotNull();
            assertThat(zip.getEntry("secrets/ai-config.key")).isNotNull();
            assertThat(zip.getEntry(".env")).isNull();
            assertThat(zip.getEntry("restore-inbox")).isNull();
            JsonNode manifest = new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json")));
            assertThat(manifest.path("backupFormatVersion").asInt()).isEqualTo(1);
            assertThat(manifest.path("files").path("database.sql").path("sha256").asText()).hasSize(64);
            assertThat(manifest.path("includesAiEncryptionKey").asBoolean()).isTrue();
        } finally {
            service.deleteArchive(archive);
        }
    }

    @Test
    void missingEncryptionKeyFailsWithoutPublishingArchive() throws Exception {
        Files.delete(key);
        when(aiConfigs.selectCount(null)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(operator(), new BackupPasswordRequest("current-password")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).contains("encryption key"));
        Path backupRoot = temp.resolve("temp").resolve("backups");
        if (Files.exists(backupRoot)) {
            try (var paths = Files.list(backupRoot)) {
                assertThat(paths.filter(path -> path.toString().endsWith(".apbackup"))).isEmpty();
            }
        }
    }

    @Test
    void concurrentBackupIsRejected() throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            lock.tryBackupLock();
            acquired.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } finally {
                lock.unlockBackup();
            }
            return null;
        });
        try {
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.create(operator(), new BackupPasswordRequest("current-password")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getMessage()).contains("already running"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void wrongPasswordFailsBeforeDatabaseOrFileBackup() {
        assertThatThrownBy(() -> service.create(operator(), new BackupPasswordRequest("wrong-password")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).contains("password"));
        verifyNoInteractions(database);
    }

    @Test
    void databaseDumpFailureCleansAllPartialArtifacts() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException("Database backup failed"))
                .when(database).dump(any(Path.class), any(Path.class));

        assertThatThrownBy(() -> service.create(operator(), new BackupPasswordRequest("current-password")))
                .isInstanceOf(BusinessException.class);

        Path backupRoot = temp.resolve("temp").resolve("backups");
        try (var paths = Files.list(backupRoot)) {
            assertThat(paths).isEmpty();
        }
    }

    @Test
    void browserDownloadLimitDeletesPublishedArchive() throws Exception {
        backup.setMaximumDownloadBytes(1);

        assertThatThrownBy(() -> service.create(
                operator(), new BackupPasswordRequest("current-password")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(413));

        Path backupRoot = temp.resolve("temp").resolve("backups");
        try (var paths = Files.list(backupRoot)) {
            assertThat(paths).isEmpty();
        }
    }

    private CurrentUser operator() {
        return new CurrentUser(1L, 2L, "admin", "admin@example.com", "SUPER_ADMIN", 0L);
    }
}
