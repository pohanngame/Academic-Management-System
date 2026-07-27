package com.example.academicprofile.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.academicprofile.file.FileStorageProperties;

class BackupTempCleanerTest {

    @TempDir
    Path temp;

    @Test
    void startupRemovesInterruptedArchiveAndPartialDirectory() throws Exception {
        Path backupRoot = Files.createDirectories(temp.resolve("backups"));
        Files.writeString(backupRoot.resolve("interrupted.apbackup"), "sensitive");
        Files.createDirectories(backupRoot.resolve("creating-partial"));

        FileStorageProperties properties = new FileStorageProperties();
        properties.setTempDir(temp.toString());
        new BackupTempCleaner(properties).removeInterruptedBackupArtifacts();

        assertThat(backupRoot).isDirectory();
        try (var entries = Files.list(backupRoot)) {
            assertThat(entries).isEmpty();
        }
    }
}
