package com.example.academicprofile.file;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationTempCleanerTest {
    @TempDir
    Path tempDir;

    @Test
    void startupCleanupDeletesOnlyApplicationPrefixedRegularFiles() throws Exception {
        Path stale = Files.writeString(
                tempDir.resolve("academic-profile-decrypted-stale.pdf"), "stale");
        Path unrelated = Files.writeString(tempDir.resolve("other-application.tmp"), "keep");
        Path matchingDirectory = Files.createDirectory(
                tempDir.resolve("academic-profile-decrypted-directory"));
        FileStorageProperties properties = new FileStorageProperties();
        properties.setTempDir(tempDir.toString());

        new ApplicationTempCleaner(properties).run(null);

        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.isDirectory(matchingDirectory));
    }
}
