package com.example.academicprofile.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import com.example.academicprofile.ai.AiProperties;
import com.example.academicprofile.ai.AiProviderConfigMapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileStorageProperties;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BackupService {

    private static final int FORMAT_VERSION = 2;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final BackupProperties properties;
    private final FileStorageProperties files;
    private final AiProperties ai;
    private final DatabaseBackupSource database;
    private final ObjectMapper json;
    private final UserAccountService users;
    private final PasswordEncoder passwords;
    private final AiProviderConfigMapper aiConfigs;
    private final SystemMaintenanceLock maintenanceLock;

    public BackupService(
            BackupProperties properties,
            FileStorageProperties files,
            AiProperties ai,
            DatabaseBackupSource database,
            ObjectMapper json,
            UserAccountService users,
            PasswordEncoder passwords,
            AiProviderConfigMapper aiConfigs,
            SystemMaintenanceLock maintenanceLock) {
        this.properties = properties;
        this.files = files;
        this.ai = ai;
        this.database = database;
        this.json = json;
        this.users = users;
        this.passwords = passwords;
        this.aiConfigs = aiConfigs;
        this.maintenanceLock = maintenanceLock;
    }

    public BackupArchive create(CurrentUser operator, BackupPasswordRequest request) {
        verifyPassword(operator.userId(), request.currentPassword());
        if (!maintenanceLock.tryBackupLock()) {
            throw new BusinessException(HttpStatus.CONFLICT, "A system backup is already running");
        }
        Path work = null;
        Path published = null;
        try {
            Path tempRoot = Path.of(files.getTempDir()).toAbsolutePath().normalize().resolve("backups");
            Files.createDirectories(tempRoot);
            checkDiskSpace(tempRoot);
            work = Files.createTempDirectory(tempRoot, "creating-");
            Path content = Files.createDirectory(work.resolve("content"));
            Path uploads = Files.createDirectories(content.resolve("uploads"));
            copyUploads(Path.of(files.getUploadDir()).toAbsolutePath().normalize(), uploads);
            copyEncryptionKey(content.resolve("secrets").resolve("ai-config.key"));
            copyFileEncryptionKey(content.resolve("secrets").resolve("file-encryption.key"));
            database.dump(content.resolve("database.sql"), work);

            Map<String, BackupManifest.FileEntry> entries = collectEntries(content);
            BackupManifest manifest = new BackupManifest(
                    FORMAT_VERSION,
                    OffsetDateTime.now(),
                    ZoneId.systemDefault().getId(),
                    properties.getApplicationVersion(),
                    properties.getGitCommit(),
                    database.flywayVersion(),
                    "MySQL",
                    database.databaseVersion(),
                    entries.size(),
                    true,
                    true,
                    entries);
            json.writerWithDefaultPrettyPrinter().writeValue(content.resolve("manifest.json").toFile(), manifest);

            String filename = "academic-profile-backup-"
                    + OffsetDateTime.now().format(FILE_TIMESTAMP) + ".apbackup";
            Path partial = work.resolve(filename + ".partial");
            zipDirectory(content, partial);
            published = tempRoot.resolve(filename);
            atomicMove(partial, published);
            long size = Files.size(published);
            if (size > properties.getMaximumDownloadBytes()) {
                Files.deleteIfExists(published);
                published = null;
                throw new BusinessException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "System backup exceeds the browser download size limit");
            }
            FileSystemUtils.deleteRecursively(work);
            return new BackupArchive(published, filename, size);
        } catch (BusinessException ex) {
            cleanupPublished(published);
            cleanup(work);
            throw ex;
        } catch (Exception ex) {
            cleanupPublished(published);
            cleanup(work);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "System backup could not be created");
        } finally {
            maintenanceLock.unlockBackup();
        }
    }

    public void deleteArchive(BackupArchive archive) {
        if (archive == null) {
            return;
        }
        try {
            Files.deleteIfExists(archive.path());
        } catch (IOException ignored) {
            // A later temporary-directory cleanup can remove this already inaccessible file.
        }
    }

    private void verifyPassword(Long userId, String currentPassword) {
        UserAccount account = users.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Current account not found"));
        if (!passwords.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Current password is incorrect");
        }
    }

    private void checkDiskSpace(Path tempRoot) throws IOException {
        long uploadBytes = directorySize(Path.of(files.getUploadDir()).toAbsolutePath().normalize());
        long required;
        try {
            required = Math.addExact(uploadBytes, properties.getMinimumFreeBytes());
        } catch (ArithmeticException ex) {
            throw new BusinessException("Insufficient disk space for system backup");
        }
        if (Files.getFileStore(tempRoot).getUsableSpace() < required) {
            throw new BusinessException("Insufficient disk space for system backup");
        }
    }

    private long directorySize(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        final long[] size = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                rejectSymlink(file);
                size[0] = Math.addExact(size[0], attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return size[0];
    }

    private void copyUploads(Path source, Path target) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(source) || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException("Upload storage is not a safe directory");
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                rejectSymlink(dir);
                Path destination = target.resolve(source.relativize(dir)).normalize();
                ensureWithin(target, destination);
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                rejectSymlink(file);
                Path destination = target.resolve(source.relativize(file)).normalize();
                ensureWithin(target, destination);
                Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyEncryptionKey(Path destination) throws IOException {
        Path source = Path.of(ai.getEncryptionKeyFile()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            long configured = aiConfigs.selectCount(null);
            String message = configured > 0
                    ? "AI encryption key is unavailable; backup was not created"
                    : "AI encryption key is unavailable; initialize AI configuration before backup";
            throw new BusinessException(message);
        }
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }

    private void copyFileEncryptionKey(Path destination) throws IOException {
        Path source = Path.of(files.getEncryptionKeyFile()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw new BusinessException("File encryption key is unavailable; backup was not created");
        }
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination);
    }

    private Map<String, BackupManifest.FileEntry> collectEntries(Path content) throws IOException {
        Map<String, BackupManifest.FileEntry> entries = new LinkedHashMap<>();
        try (var paths = Files.walk(content)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = content.relativize(path).toString().replace('\\', '/');
                entries.put(name, new BackupManifest.FileEntry(Files.size(path), sha256(path)));
            }
        }
        return entries;
    }

    private String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void zipDirectory(Path content, Path output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(output)))) {
            try (var paths = Files.walk(content)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String name = content.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void rejectSymlink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new BusinessException("Symbolic links are not allowed in backup content");
        }
    }

    private void ensureWithin(Path root, Path path) {
        if (!path.startsWith(root.toAbsolutePath().normalize())) {
            throw new BusinessException("Unsafe backup content path");
        }
    }

    private void cleanup(Path work) {
        if (work != null) {
            try {
                FileSystemUtils.deleteRecursively(work);
            } catch (IOException ignored) {
                // The temp directory remains outside downloadable backup names.
            }
        }
    }

    private void cleanupPublished(Path published) {
        if (published != null) {
            try {
                Files.deleteIfExists(published);
            } catch (IOException ignored) {
                // Startup cleanup is the final fallback for an inaccessible temporary archive.
            }
        }
    }

}
