package com.example.academicprofile.backup;

import java.nio.file.Files;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {

    private final BackupService backups;
    private final AdminAuditService audit;

    public BackupController(BackupService backups, AdminAuditService audit) {
        this.backups = backups;
        this.audit = audit;
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @Valid @RequestBody BackupPasswordRequest request) {
        CurrentUser operator = SecurityUtils.currentUser();
        BackupArchive archive = null;
        try {
            archive = backups.create(operator, request);
            audit.record(operator.userId(), "BACKUP_CREATE", null, true,
                    "System migration backup created");
        } catch (RuntimeException ex) {
            backups.deleteArchive(archive);
            recordFailure(operator.userId(), "BACKUP_FAILED", "System migration backup creation failed");
            throw ex;
        }

        final BackupArchive downloadable = archive;
        try {
            StreamingResponseBody body = output -> {
                boolean success = false;
                try {
                    Files.copy(downloadable.path(), output);
                    output.flush();
                    success = true;
                } finally {
                    backups.deleteArchive(downloadable);
                    recordFailure(operator.userId(), "BACKUP_DOWNLOAD",
                            success
                                    ? "System migration backup downloaded"
                                    : "System migration backup download failed",
                            success);
                }
            };
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(downloadable.size());
            headers.setCacheControl("no-store");
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(downloadable.filename(), java.nio.charset.StandardCharsets.UTF_8)
                    .build());
            return ResponseEntity.ok().headers(headers).body(body);
        } catch (RuntimeException ex) {
            backups.deleteArchive(downloadable);
            recordFailure(operator.userId(), "BACKUP_DOWNLOAD",
                    "System migration backup download preparation failed");
            throw ex;
        }
    }

    private void recordFailure(Long operatorId, String action, String summary) {
        recordFailure(operatorId, action, summary, false);
    }

    private void recordFailure(Long operatorId, String action, String summary, boolean success) {
        try {
            audit.record(operatorId, action, null, success, summary);
        } catch (RuntimeException ignored) {
            // Audit storage failure must not expose backup content or credentials.
        }
    }
}
