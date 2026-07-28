package com.example.academicprofile.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.admin.AdminAuditService;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class RestoreStagingService {

    private static final String STATUS_STAGED = "STAGED";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> OWNER_DIRECTORY_ONLY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    private final RestoreStagingProperties properties;
    private final UserAccountService users;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final AdminAuditService audit;
    private final Set<UUID> activeUploads = ConcurrentHashMap.newKeySet();

    public RestoreStagingService(
            RestoreStagingProperties properties,
            UserAccountService users,
            PasswordEncoder passwords,
            ObjectMapper json,
            AdminAuditService audit) {
        this.properties = properties;
        this.users = users;
        this.passwords = passwords;
        this.json = json;
        this.audit = audit;
    }

    @PostConstruct
    public void initializeInbox() {
        try {
            Files.createDirectories(inbox());
            restrictDirectoryPermissions(inbox());
            cleanupInterruptedPartials();
            cleanupExpired();
            cleanupOrphans();
        } catch (IOException ex) {
            throw new IllegalStateException("Restore staging storage is unavailable");
        }
    }

    public RestoreStageMetadata stage(
            CurrentUser operator, MultipartFile file, String currentPassword) {
        UUID id = UUID.randomUUID();
        Path packagePartial = packagePath(id, ".apbackup.partial");
        Path packagePublished = packagePath(id, ".apbackup");
        Path metadataPartial = packagePath(id, ".json.partial");
        Path metadataPublished = packagePath(id, ".json");
        activeUploads.add(id);
        try {
            verifyPassword(operator.userId(), currentPassword);
            validateUpload(file);
            UploadDigest digest = streamToPartial(file, packagePartial);
            OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
            RestoreStageMetadata metadata = new RestoreStageMetadata(
                    id,
                    digest.size(),
                    digest.sha256(),
                    createdAt,
                    createdAt.plus(properties.getStagedTtl()),
                    STATUS_STAGED);
            atomicPublish(packagePartial, packagePublished);
            writeMetadata(metadataPartial, metadata);
            atomicPublish(metadataPartial, metadataPublished);
            try {
                audit.record(operator.userId(), "RESTORE_STAGE", null, true,
                        "Restore package staged: id=" + id + ", size=" + digest.size());
            } catch (RuntimeException ex) {
                throw new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Restore package could not be staged");
            }
            return metadata;
        } catch (BusinessException ex) {
            deleteArtifacts(id);
            recordFailure(operator.userId(), id, "Restore package staging rejected");
            throw ex;
        } catch (Exception ex) {
            deleteArtifacts(id);
            recordFailure(operator.userId(), id, "Restore package staging failed");
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Restore package could not be staged");
        } finally {
            activeUploads.remove(id);
        }
    }

    public List<RestoreStageMetadata> list() {
        cleanupExpiredSafely();
        Path root = inbox();
        try (var paths = Files.list(root)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readMetadataSafely)
                    .filter(java.util.Objects::nonNull)
                    .filter(metadata -> Files.isRegularFile(
                            packagePath(metadata.id(), ".apbackup"), LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(RestoreStageMetadata::createdAt).reversed())
                    .toList();
        } catch (IOException ex) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Restore package list is unavailable");
        }
    }

    public void delete(CurrentUser operator, String rawId, RestorePasswordRequest request) {
        UUID id = parseId(rawId);
        try {
            verifyPassword(operator.userId(), request.currentPassword());
            boolean existed = Files.isRegularFile(
                    packagePath(id, ".apbackup"), LinkOption.NOFOLLOW_LINKS);
            if (!existed) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "Staged restore package not found");
            }
            deleteArtifactsStrict(id);
            try {
                audit.record(operator.userId(), "RESTORE_STAGE_DELETE", null, true,
                        "Staged restore package deleted: id=" + id);
            } catch (RuntimeException ignored) {
                // The file deletion is already complete and must not be reported as failed.
            }
        } catch (BusinessException ex) {
            recordFailure(operator.userId(), id, "Staged restore package deletion rejected");
            throw ex;
        } catch (Exception ex) {
            recordFailure(operator.userId(), id, "Staged restore package deletion failed");
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Staged restore package could not be deleted");
        }
    }

    @Scheduled(fixedDelayString = "${app.restore.cleanup-interval:PT1H}")
    public void scheduledCleanup() {
        cleanupExpiredSafely();
        try {
            cleanupInterruptedPartials();
            cleanupOrphans();
        } catch (IOException ignored) {
            // A later cleanup will retry without exposing storage paths.
        }
    }

    void cleanupExpired() throws IOException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try (var paths = Files.list(inbox())) {
            for (Path metadataPath : paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList()) {
                RestoreStageMetadata metadata = readMetadataSafely(metadataPath);
                if (metadata == null || !metadata.expiresAt().isAfter(now)) {
                    UUID id = idFromMetadataFilename(metadataPath);
                    if (id != null && !activeUploads.contains(id)) {
                        deleteArtifacts(id);
                    }
                }
            }
        }
    }

    void cleanupInterruptedPartials() throws IOException {
        try (var paths = Files.list(inbox())) {
            for (Path path : paths
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".partial"))
                    .toList()) {
                UUID id = idFromPartialFilename(path);
                if (id != null && !activeUploads.contains(id)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    void cleanupOrphans() throws IOException {
        try (var paths = Files.list(inbox())) {
            for (Path path : paths
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".apbackup"))
                    .toList()) {
                String name = path.getFileName().toString();
                UUID id = parseFilenameId(name.substring(0, name.length() - ".apbackup".length()));
                if (id != null
                        && !activeUploads.contains(id)
                        && !Files.isRegularFile(packagePath(id, ".json"), LinkOption.NOFOLLOW_LINKS)) {
                    deleteArtifacts(id);
                }
            }
        }
    }

    private UploadDigest streamToPartial(MultipartFile file, Path partial) throws IOException {
        MessageDigest messageDigest = sha256Digest();
        long size = 0;
        byte[] signature = new byte[4];
        int signatureLength = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = file.getInputStream();
                OutputStream output = Files.newOutputStream(
                        partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            restrictPermissions(partial);
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                if (signatureLength < signature.length) {
                    int copied = Math.min(read, signature.length - signatureLength);
                    System.arraycopy(buffer, 0, signature, signatureLength, copied);
                    signatureLength += copied;
                }
                size = Math.addExact(size, read);
                if (size > properties.getMaxUploadBytes()) {
                    throw new BusinessException(
                            HttpStatus.PAYLOAD_TOO_LARGE, "Restore package exceeds size limit");
                }
                messageDigest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (size == 0) {
            throw new BusinessException("Restore package must not be empty");
        }
        if (!isZipSignature(signature, signatureLength)) {
            throw new BusinessException("Restore package is not a ZIP archive");
        }
        return new UploadDigest(size, HexFormat.of().formatHex(messageDigest.digest()));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Restore package must not be empty");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)
                || !filename.toLowerCase(Locale.ROOT).endsWith(".apbackup")) {
            throw new BusinessException("File extension must be .apbackup");
        }
        if (properties.getMaxUploadBytes() <= 0) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Restore upload limit is invalid");
        }
    }

    private void verifyPassword(Long userId, String currentPassword) {
        if (!StringUtils.hasText(currentPassword)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Current password is incorrect");
        }
        UserAccount account = users.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED, "Current account not found"));
        if (!passwords.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Current password is incorrect");
        }
    }

    private void writeMetadata(Path partial, RestoreStageMetadata metadata) throws IOException {
        try (OutputStream output = Files.newOutputStream(
                partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            restrictPermissions(partial);
            json.writeValue(output, metadata);
        }
    }

    private RestoreStageMetadata readMetadataSafely(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            RestoreStageMetadata metadata = json.readValue(path.toFile(), RestoreStageMetadata.class);
            UUID filenameId = idFromMetadataFilename(path);
            if (filenameId == null
                    || !filenameId.equals(metadata.id())
                    || metadata.size() <= 0
                    || metadata.sha256() == null
                    || !metadata.sha256().matches("^[0-9a-f]{64}$")
                    || metadata.createdAt() == null
                    || metadata.expiresAt() == null
                    || !STATUS_STAGED.equals(metadata.status())) {
                return null;
            }
            return metadata;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void atomicPublish(Path partial, Path published) throws IOException {
        try {
            Files.move(partial, published, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            throw new IOException("Atomic restore package publication is unavailable");
        }
    }

    private void cleanupExpiredSafely() {
        try {
            cleanupExpired();
        } catch (IOException ignored) {
            // Listing remains safe; the next scheduled cleanup will retry.
        }
    }

    private void deleteArtifacts(UUID id) {
        for (String suffix : List.of(
                ".apbackup.partial", ".apbackup", ".json.partial", ".json")) {
            try {
                Files.deleteIfExists(packagePath(id, suffix));
            } catch (IOException ignored) {
                // A later scheduled cleanup retries inaccessible artifacts.
            }
        }
    }

    private void deleteArtifactsStrict(UUID id) throws IOException {
        for (String suffix : List.of(
                ".apbackup.partial", ".apbackup", ".json.partial", ".json")) {
            Files.deleteIfExists(packagePath(id, suffix));
        }
    }

    private void recordFailure(Long operatorId, UUID id, String summary) {
        try {
            audit.record(operatorId, "RESTORE_STAGE_FAILED", null, false,
                    summary + ": id=" + id);
        } catch (RuntimeException ignored) {
            // Audit failure must not replace the controlled staging error.
        }
    }

    private UUID parseId(String rawId) {
        if (rawId == null || !UUID_PATTERN.matcher(rawId).matches()) {
            throw new BusinessException("Invalid restore task ID");
        }
        return UUID.fromString(rawId);
    }

    private UUID idFromMetadataFilename(Path path) {
        String name = path.getFileName().toString();
        if (!name.endsWith(".json")) {
            return null;
        }
        return parseFilenameId(name.substring(0, name.length() - ".json".length()));
    }

    private UUID idFromPartialFilename(Path path) {
        String name = path.getFileName().toString();
        int suffix = name.indexOf('.');
        return suffix > 0 ? parseFilenameId(name.substring(0, suffix)) : null;
    }

    private UUID parseFilenameId(String value) {
        try {
            return UUID_PATTERN.matcher(value).matches() ? UUID.fromString(value) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Path inbox() {
        return Path.of(properties.getInboxDir()).toAbsolutePath().normalize();
    }

    private Path packagePath(UUID id, String suffix) {
        Path root = inbox();
        Path resolved = root.resolve(id + suffix).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("Invalid restore task path");
        }
        return resolved;
    }

    private boolean isZipSignature(byte[] signature, int length) {
        if (length < 4 || signature[0] != 'P' || signature[1] != 'K') {
            return false;
        }
        return (signature[2] == 3 && signature[3] == 4)
                || (signature[2] == 5 && signature[3] == 6)
                || (signature[2] == 7 && signature[3] == 8);
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (UnsupportedOperationException | IOException ignored) {
            java.io.File file = path.toFile();
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, true);
            file.setWritable(true, true);
        }
    }

    private void restrictDirectoryPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, OWNER_DIRECTORY_ONLY);
        } catch (UnsupportedOperationException | IOException ignored) {
            java.io.File directory = path.toFile();
            directory.setReadable(false, false);
            directory.setWritable(false, false);
            directory.setExecutable(false, false);
            directory.setReadable(true, true);
            directory.setWritable(true, true);
            directory.setExecutable(true, true);
        }
    }

    private record UploadDigest(long size, String sha256) {
    }
}
