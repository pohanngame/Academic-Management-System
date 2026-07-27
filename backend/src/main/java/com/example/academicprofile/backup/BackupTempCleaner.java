package com.example.academicprofile.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import com.example.academicprofile.file.FileStorageProperties;

import jakarta.annotation.PostConstruct;

@Component
public class BackupTempCleaner {

    private final FileStorageProperties files;

    public BackupTempCleaner(FileStorageProperties files) {
        this.files = files;
    }

    @PostConstruct
    public void removeInterruptedBackupArtifacts() throws IOException {
        Path tempRoot = Path.of(files.getTempDir()).toAbsolutePath().normalize();
        Path backupRoot = tempRoot.resolve("backups").normalize();
        if (!backupRoot.startsWith(tempRoot)) {
            throw new IOException("Invalid backup temporary directory");
        }
        if (Files.isDirectory(backupRoot)) {
            try (var entries = Files.list(backupRoot)) {
                for (Path entry : entries.toList()) {
                    FileSystemUtils.deleteRecursively(entry);
                }
            }
        }
        Files.createDirectories(backupRoot);
    }
}
