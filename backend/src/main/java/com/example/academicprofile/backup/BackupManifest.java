package com.example.academicprofile.backup;

import java.time.OffsetDateTime;
import java.util.Map;

public record BackupManifest(
        int backupFormatVersion,
        OffsetDateTime createdAt,
        String timezone,
        String applicationVersion,
        String gitCommit,
        String flywaySchemaVersion,
        String databaseType,
        String databaseVersion,
        long fileCount,
        boolean includesAiEncryptionKey,
        boolean includesFileEncryptionKey,
        Map<String, FileEntry> files) {

    public record FileEntry(long size, String sha256) {
    }
}
