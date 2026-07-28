package com.example.academicprofile.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;

class RestoreStagingServiceTest {

    @TempDir
    Path temp;

    private RestoreStagingProperties properties;
    private UserAccountService users;
    private PasswordEncoder passwords;
    private AdminAuditService audit;
    private ObjectMapper json;
    private RestoreStagingService service;

    @BeforeEach
    void setUp() {
        properties = new RestoreStagingProperties();
        properties.setInboxDir(temp.resolve("inbox").toString());
        properties.setMaxUploadBytes(1024 * 1024);
        properties.setStagedTtl(Duration.ofHours(1));
        users = mock(UserAccountService.class);
        passwords = mock(PasswordEncoder.class);
        audit = mock(AdminAuditService.class);
        json = new ObjectMapper().findAndRegisterModules();
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setPasswordHash("bcrypt");
        when(users.findById(1L)).thenReturn(Optional.of(account));
        when(passwords.matches("correct-password", "bcrypt")).thenReturn(true);
        service = new RestoreStagingService(properties, users, passwords, json, audit);
        service.initializeInbox();
    }

    @Test
    void wrongPasswordCreatesNoFilesAndRecordsSafeFailure() throws Exception {
        assertThatThrownBy(() -> service.stage(operator(), packageFile("backup.apbackup"), "wrong"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));

        assertThat(inboxFiles()).isEmpty();
        verify(audit).record(eq(1L), eq("RESTORE_STAGE_FAILED"), eq(null), eq(false),
                contains("id="));
    }

    @Test
    void rejectsWrongExtensionEmptyAndInvalidZipWithoutResidue() throws Exception {
        assertThatThrownBy(() -> service.stage(
                operator(),
                new MockMultipartFile("file", "backup.zip", "application/zip", zipBytes()),
                "correct-password")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.stage(
                operator(),
                new MockMultipartFile("file", "backup.apbackup", "application/octet-stream", new byte[0]),
                "correct-password")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.stage(
                operator(),
                new MockMultipartFile("file", "backup.apbackup", "application/octet-stream", "no".getBytes()),
                "correct-password")).isInstanceOf(BusinessException.class);

        assertThat(inboxFiles()).isEmpty();
    }

    @Test
    void overLimitStopsStreamingAndDeletesPartial() throws Exception {
        properties.setMaxUploadBytes(8);

        assertThatThrownBy(() -> service.stage(
                operator(), packageFile("large.apbackup"), "correct-password"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(413));

        assertThat(inboxFiles()).isEmpty();
    }

    @Test
    void successfulUploadUsesUuidAndPublishesAccurateMetadataAtomically() throws Exception {
        MockMultipartFile file = packageFile("../private\\ name.apbackup");

        RestoreStageMetadata metadata = service.stage(operator(), file, "correct-password");

        Path packagePath = temp.resolve("inbox").resolve(metadata.id() + ".apbackup");
        Path sidecar = temp.resolve("inbox").resolve(metadata.id() + ".json");
        assertThat(packagePath).isRegularFile();
        assertThat(sidecar).isRegularFile();
        assertThat(packagePath.getFileName().toString()).doesNotContain("private");
        assertThat(metadata.size()).isEqualTo(Files.size(packagePath));
        assertThat(metadata.sha256()).matches("^[0-9a-f]{64}$");
        assertThat(json.readValue(sidecar.toFile(), RestoreStageMetadata.class)).isEqualTo(metadata);
        assertThat(inboxFiles()).noneMatch(path -> path.toString().endsWith(".partial"));
        verify(audit).record(1L, "RESTORE_STAGE", null, true,
                "Restore package staged: id=" + metadata.id() + ", size=" + metadata.size());
    }

    @Test
    void successAuditFailureRemovesPublishedPackageAndSidecar() throws Exception {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).record(eq(1L), eq("RESTORE_STAGE"), eq(null), eq(true), any());

        assertThatThrownBy(() -> service.stage(
                operator(), packageFile("backup.apbackup"), "correct-password"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).doesNotContain("audit"));

        assertThat(inboxFiles()).isEmpty();
    }

    @Test
    void listKeepsUnexpiredPackageAndCleanupRemovesExpiredPackage() throws Exception {
        RestoreStageMetadata current = service.stage(
                operator(), packageFile("current.apbackup"), "correct-password");
        assertThat(service.list()).extracting(RestoreStageMetadata::id).containsExactly(current.id());

        properties.setStagedTtl(Duration.ofSeconds(-1));
        RestoreStageMetadata expired = service.stage(
                operator(), packageFile("expired.apbackup"), "correct-password");
        service.cleanupExpired();

        assertThat(temp.resolve("inbox").resolve(expired.id() + ".apbackup")).doesNotExist();
        assertThat(temp.resolve("inbox").resolve(expired.id() + ".json")).doesNotExist();
        assertThat(temp.resolve("inbox").resolve(current.id() + ".apbackup")).exists();
    }

    @Test
    void interruptedPartialsAreRemovedWithoutTouchingPublishedFiles() throws Exception {
        UUID id = UUID.randomUUID();
        Path partial = temp.resolve("inbox").resolve(id + ".apbackup.partial");
        UUID publishedId = UUID.randomUUID();
        Path published = temp.resolve("inbox").resolve(publishedId + ".apbackup");
        Path sidecar = temp.resolve("inbox").resolve(publishedId + ".json");
        Files.writeString(partial, "interrupted");
        Files.writeString(published, "published");
        Files.writeString(sidecar, "{}");

        service.cleanupInterruptedPartials();

        assertThat(partial).doesNotExist();
        assertThat(published).exists();
    }

    @Test
    void orphanPublishedPackageIsCleaned() throws Exception {
        UUID id = UUID.randomUUID();
        Path orphan = temp.resolve("inbox").resolve(id + ".apbackup");
        Files.writeString(orphan, "interrupted-before-sidecar");

        service.cleanupOrphans();

        assertThat(orphan).doesNotExist();
    }

    @Test
    void deleteRejectsNonUuidAndCannotEscapeInbox() throws Exception {
        assertThatThrownBy(() -> service.delete(
                operator(), "../outside", new RestorePasswordRequest("correct-password")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).doesNotContain(temp.toString()));

        assertThat(Files.exists(temp.resolve("outside"))).isFalse();
    }

    @Test
    void deleteRemovesPackageAndSidecarOnlyAfterPasswordVerification() throws Exception {
        RestoreStageMetadata metadata = service.stage(
                operator(), packageFile("backup.apbackup"), "correct-password");

        assertThatThrownBy(() -> service.delete(
                operator(), metadata.id().toString(), new RestorePasswordRequest("wrong")))
                .isInstanceOf(BusinessException.class);
        assertThat(temp.resolve("inbox").resolve(metadata.id() + ".apbackup")).exists();

        service.delete(
                operator(), metadata.id().toString(), new RestorePasswordRequest("correct-password"));

        assertThat(temp.resolve("inbox").resolve(metadata.id() + ".apbackup")).doesNotExist();
        assertThat(temp.resolve("inbox").resolve(metadata.id() + ".json")).doesNotExist();
        verify(audit).record(1L, "RESTORE_STAGE_DELETE", null, true,
                "Staged restore package deleted: id=" + metadata.id());
    }

    @Test
    void passwordRequestToStringIsRedacted() {
        String text = new RestorePasswordRequest("do-not-leak").toString();
        assertThat(text).doesNotContain("do-not-leak").contains("[redacted]");
    }

    private CurrentUser operator() {
        return new CurrentUser(1L, 1L, "admin", "admin@example.com", "SUPER_ADMIN", 0L);
    }

    private MockMultipartFile packageFile(String filename) throws Exception {
        return new MockMultipartFile(
                "file", filename, "application/octet-stream", zipBytes());
    }

    private byte[] zipBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write("{}".getBytes());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private java.util.List<Path> inboxFiles() throws Exception {
        try (var files = Files.list(temp.resolve("inbox"))) {
            return files.toList();
        }
    }
}
