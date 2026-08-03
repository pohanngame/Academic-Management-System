package com.example.academicprofile.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.academicprofile.common.exception.BusinessException;

@Component
public class ApplicationTempCleaner implements ApplicationRunner {
    static final String DECRYPTED_PREFIX = "academic-profile-decrypted-";

    private final FileStorageProperties properties;

    public ApplicationTempCleaner(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path root = Path.of(properties.getTempDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            restrictDirectory(root);
            try (var entries = Files.list(root)) {
                entries.filter(path -> path.getFileName().toString().startsWith(DECRYPTED_PREFIX))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .forEach(this::deleteQuietly);
            }
        } catch (IOException ex) {
            throw new BusinessException("Application temporary directory is unavailable");
        }
    }

    private void restrictDirectory(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Platform ACLs apply when POSIX permissions are unavailable.
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A later startup or the tmpfs lifecycle can retry cleanup.
        }
    }
}
