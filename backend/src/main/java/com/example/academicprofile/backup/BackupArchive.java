package com.example.academicprofile.backup;

import java.nio.file.Path;

public record BackupArchive(Path path, String filename, long size) {
}
