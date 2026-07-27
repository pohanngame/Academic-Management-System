package com.example.academicprofile.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.security.CurrentUser;

class BackupControllerTest {

    @TempDir
    Path temp;

    private BackupService backups;
    private AdminAuditService audit;
    private BackupController controller;
    private BackupArchive archive;

    @BeforeEach
    void setUp() throws Exception {
        Path path = temp.resolve("test.apbackup");
        Files.writeString(path, "sensitive-backup");
        archive = new BackupArchive(path, "test.apbackup", Files.size(path));
        backups = org.mockito.Mockito.mock(BackupService.class);
        audit = org.mockito.Mockito.mock(AdminAuditService.class);
        when(backups.create(any(), any())).thenReturn(archive);
        doAnswer(invocation -> {
            Files.deleteIfExists(invocation.getArgument(0, BackupArchive.class).path());
            return null;
        }).when(backups).deleteArchive(any());
        controller = new BackupController(backups, audit);

        CurrentUser principal = new CurrentUser(
                1L, 1L, "admin", "admin@example.com", "SUPER_ADMIN", 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulAuditFailureDeletesPublishedArchiveImmediately() {
        doAnswer(invocation -> {
            if (invocation.getArgument(3, Boolean.class)) {
                throw new IllegalStateException("audit unavailable");
            }
            return null;
        }).when(audit).record(any(), any(), any(), any(Boolean.class), any());

        assertThatThrownBy(() -> controller.export(new BackupPasswordRequest("current-password")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(archive.path()).doesNotExist();
    }

    @Test
    void completedDownloadDeletesArchiveAndRecordsDownloadSuccess() throws Exception {
        StreamingResponseBody body = controller.export(
                new BackupPasswordRequest("current-password")).getBody();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        body.writeTo(output);

        assertThat(output.toString()).isEqualTo("sensitive-backup");
        assertThat(archive.path()).doesNotExist();
        verify(audit).record(1L, "BACKUP_DOWNLOAD", null, true,
                "System migration backup downloaded");
    }

    @Test
    void interruptedDownloadDeletesArchiveAndRecordsDownloadFailure() throws Exception {
        StreamingResponseBody body = controller.export(
                new BackupPasswordRequest("current-password")).getBody();
        OutputStream interrupted = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("client disconnected");
            }
        };

        assertThatThrownBy(() -> body.writeTo(interrupted))
                .isInstanceOf(IOException.class);

        assertThat(archive.path()).doesNotExist();
        verify(audit).record(1L, "BACKUP_DOWNLOAD", null, false,
                "System migration backup download failed");
    }

    @Test
    void responsePreparationFailureDeletesArchiveAndRecordsDownloadFailure() {
        BackupArchive invalid = org.mockito.Mockito.mock(BackupArchive.class);
        when(invalid.path()).thenReturn(archive.path());
        when(invalid.size()).thenReturn(archive.size());
        when(invalid.filename()).thenThrow(new IllegalStateException("header construction failed"));
        when(backups.create(any(), any())).thenReturn(invalid);

        assertThatThrownBy(() -> controller.export(new BackupPasswordRequest("current-password")))
                .isInstanceOf(RuntimeException.class);

        assertThat(archive.path()).doesNotExist();
        verify(audit).record(eq(1L), eq("BACKUP_DOWNLOAD"), any(), eq(false),
                eq("System migration backup download preparation failed"));
    }
}
