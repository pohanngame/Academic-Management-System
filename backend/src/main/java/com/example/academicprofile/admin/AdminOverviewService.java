package com.example.academicprofile.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.example.academicprofile.file.FileStorageProperties;
import com.example.academicprofile.user.UserAccountService;

@Service
public class AdminOverviewService {

    private final UserAccountService users;
    private final FileStorageProperties fileProperties;
    private final AdminAuditService audit;

    public AdminOverviewService(
            UserAccountService users,
            FileStorageProperties fileProperties,
            AdminAuditService audit) {
        this.users = users;
        this.fileProperties = fileProperties;
        this.audit = audit;
    }

    public AdminOverviewResponse get() {
        StorageUsage storage = storageUsage();
        AdminAuditLog latest = audit.latestBackupResult();
        AdminOverviewResponse.BackupResult backup = latest == null ? null
                : new AdminOverviewResponse.BackupResult(
                        Boolean.TRUE.equals(latest.getSuccess()), latest.getSummary(), latest.getCreatedAt());
        return new AdminOverviewResponse(
                users.countBy(null, null),
                users.countBy("TEACHER", null),
                users.countBy("SUPER_ADMIN", null),
                users.countBy(null, "ACTIVE"),
                users.countBy(null, "DISABLED"),
                storage.files(),
                storage.bytes(),
                backup);
    }

    private StorageUsage storageUsage() {
        Path root = Path.of(fileProperties.getUploadDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return new StorageUsage(0, 0);
        }
        long files = 0;
        long bytes = 0;
        try (var paths = Files.walk(root)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (!path.normalize().startsWith(root)
                        || Files.isSymbolicLink(path)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                files++;
                bytes = Math.addExact(bytes, Files.size(path));
            }
            return new StorageUsage(files, bytes);
        } catch (IOException | ArithmeticException ex) {
            return new StorageUsage(0, 0);
        }
    }

    private record StorageUsage(long files, long bytes) {
    }
}
