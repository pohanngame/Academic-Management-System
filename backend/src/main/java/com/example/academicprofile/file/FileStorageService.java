package com.example.academicprofile.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.core.io.Resource;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.academicprofile.achievement.Certificate;
import com.example.academicprofile.achievement.CertificateMapper;
import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.achievement.Patent;
import com.example.academicprofile.achievement.PatentMapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.access.AccessPermission;
import com.example.academicprofile.access.ResourceAuthorizationService;
import com.example.academicprofile.access.ResourcePolicyService;
import com.example.academicprofile.access.ResourceType;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfile;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.word.WordTemplateService;

@Service
public class FileStorageService {

    private static final Map<String, Set<String>> ATTACHMENT_ALLOWED_MIME_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "webp", Set.of("image/webp"));
    private static final Map<String, Set<String>> IMAGE_ALLOWED_MIME_TYPES = Map.of(
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "webp", Set.of("image/webp"));
    private static final Map<String, Set<String>> AI_WORD_TEMPLATE_ALLOWED_MIME_TYPES = Map.of(
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/octet-stream"));
    private static final Set<String> READABLE_TEMP_EXTENSIONS =
            Set.of("pdf", "jpg", "jpeg", "png", "webp");

    private final FileMetadataMapper fileMetadataMapper;
    private final FileStorageProperties properties;
    private final TeacherProfileService teacherProfileService;
    private final PaperMapper paperMapper;
    private final PatentMapper patentMapper;
    private final CertificateMapper certificateMapper;
    private final WordTemplateService wordTemplateService;
    private final EncryptedFileCodec encryptedFiles;
    private final ResourceAuthorizationService authorization;
    private final ResourcePolicyService policies;

    public FileStorageService(
            FileMetadataMapper fileMetadataMapper,
            FileStorageProperties properties,
            TeacherProfileService teacherProfileService,
            PaperMapper paperMapper,
            PatentMapper patentMapper,
            CertificateMapper certificateMapper,
            WordTemplateService wordTemplateService,
            EncryptedFileCodec encryptedFiles,
            ResourceAuthorizationService authorization,
            ResourcePolicyService policies) {
        this.fileMetadataMapper = fileMetadataMapper;
        this.properties = properties;
        this.teacherProfileService = teacherProfileService;
        this.paperMapper = paperMapper;
        this.patentMapper = patentMapper;
        this.certificateMapper = certificateMapper;
        this.wordTemplateService = wordTemplateService;
        this.encryptedFiles = encryptedFiles;
        this.authorization = authorization;
        this.policies = policies;
    }

    public List<FileMetadata> listFiles(Long teacherId, FileBusinessType businessType, Long businessId) {
        validateBusinessOwnership(teacherId, businessType, businessId);
        return fileMetadataMapper.selectList(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getTeacherId, teacherId)
                .eq(FileMetadata::getBusinessType, businessType.name())
                .eq(businessId != null, FileMetadata::getBusinessId, businessId)
                .isNull(businessId == null, FileMetadata::getBusinessId)
                .eq(FileMetadata::getDeleted, Boolean.FALSE)
                .orderByDesc(FileMetadata::getId));
    }

    public List<FileMetadata> listOwnedAttachments(Long teacherId) {
        return fileMetadataMapper.selectList(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getTeacherId, teacherId)
                .ne(FileMetadata::getBusinessType, FileBusinessType.AVATAR.name())
                .eq(FileMetadata::getDeleted, Boolean.FALSE)
                .orderByDesc(FileMetadata::getId)
                .last("LIMIT 100"));
    }

    public Page<FileMetadata> pageOwnedAttachments(
            Long teacherId, String search, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("Invalid pagination");
        }
        String normalized = StringUtils.hasText(search) ? search.trim() : null;
        return fileMetadataMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<FileMetadata>()
                        .eq(FileMetadata::getTeacherId, teacherId)
                        .ne(FileMetadata::getBusinessType, FileBusinessType.AVATAR.name())
                        .eq(FileMetadata::getDeleted, Boolean.FALSE)
                        .like(normalized != null, FileMetadata::getOriginalName, normalized)
                        .orderByDesc(FileMetadata::getId));
    }

    public List<FileMetadata> listSharedFiles(
            CurrentUser actor,
            FileBusinessType businessType,
            Long businessId,
            AccessPermission permission) {
        if (businessId == null
                || businessType == FileBusinessType.AVATAR
                || businessType == FileBusinessType.AI_WORD_TEMPLATE) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Files not found");
        }
        ResourceType type = ResourceType.fromFileBusinessType(businessType.name());
        if (permission == AccessPermission.AI_USE) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Files not found");
        }
        Long ownerTeacherId = permission == AccessPermission.VIEW
                ? authorization.require(actor, type, businessId, AccessPermission.VIEW).ownerTeacherId()
                : authorization.requireOwnerViewEnabled(actor, type, businessId);
        return fileMetadataMapper.selectList(new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getTeacherId, ownerTeacherId)
                .eq(FileMetadata::getBusinessType, businessType.name())
                .eq(FileMetadata::getBusinessId, businessId)
                .eq(FileMetadata::getDeleted, Boolean.FALSE)
                .orderByAsc(FileMetadata::getId)).stream()
                .filter(metadata -> canReadAttachment(actor, metadata, permission))
                .toList();
    }

    @Transactional
    public FileMetadata uploadFile(Long teacherId, FileBusinessType businessType, Long businessId, MultipartFile file) {
        validateBusinessOwnership(teacherId, businessType, businessId);
        validateFile(file, businessType);

        String originalName = safeOriginalName(file.getOriginalFilename());
        String fileExt = extensionOf(originalName);
        boolean encrypted = businessType != FileBusinessType.AVATAR;
        String storedName = encrypted ? UUID.randomUUID() + ".bin" : UUID.randomUUID() + "." + fileExt;
        Path relativePath = relativePath(teacherId, businessType, storedName);
        Path targetPath = resolveStoragePath(relativePath.toString());
        Path partialPath = targetPath.resolveSibling(targetPath.getFileName() + ".partial");
        EncryptedFileCodec.EncryptionResult encryption = null;

        try {
            Files.createDirectories(targetPath.getParent());
            if (encrypted) {
                try (InputStream input = file.getInputStream()) {
                    encryption = encryptedFiles.encrypt(input, partialPath, storedName);
                }
                restrictFile(partialPath);
                atomicMove(partialPath, targetPath);
            } else {
                file.transferTo(partialPath);
                restrictFile(partialPath);
                atomicMove(partialPath, targetPath);
            }
        } catch (IOException ex) {
            deleteStoredFile(partialPath);
            deleteStoredFile(targetPath);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        } catch (RuntimeException ex) {
            deleteStoredFile(partialPath);
            deleteStoredFile(targetPath);
            throw ex;
        }

        FileMetadata metadata = new FileMetadata();
        metadata.setTeacherId(teacherId);
        metadata.setOriginalName(originalName);
        metadata.setStoredName(storedName);
        metadata.setStoragePath(toStoragePath(relativePath));
        metadata.setFileExt(fileExt);
        metadata.setMimeType(file.getContentType());
        metadata.setFileSize(file.getSize());
        metadata.setBusinessType(businessType.name());
        metadata.setBusinessId(businessId);
        metadata.setEncryptionVersion(encrypted ? EncryptedFileCodec.VERSION : null);
        metadata.setEncryptionKeyId(encrypted ? encryption.keyId() : null);
        metadata.setWrappedDek(encrypted ? encryption.wrappedDek() : null);
        metadata.setCiphertextStatus(encrypted ? "ENCRYPTED" : "PLAINTEXT");
        metadata.setPlaintextSha256(encrypted ? encryption.plaintextSha256() : null);
        metadata.setDeleted(Boolean.FALSE);
        try {
            fileMetadataMapper.insert(metadata);
            if (businessType != FileBusinessType.AVATAR) {
                policies.createDefault(teacherId, ResourceType.FILE_ATTACHMENT, metadata.getId());
            }
            registerRollbackCleanup(targetPath);
        } catch (RuntimeException ex) {
            deleteStoredFile(targetPath);
            throw ex;
        }
        return metadata;
    }

    private void registerRollbackCleanup(Path targetPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteStoredFile(targetPath);
                }
            }
        });
    }

    @Transactional
    public FileMetadata uploadAvatar(Long teacherId, MultipartFile file) {
        FileMetadata metadata = uploadFile(teacherId, FileBusinessType.AVATAR, null, file);
        teacherProfileService.updateAvatarFileId(teacherId, metadata.getId());
        return metadata;
    }

    public FileDownload downloadFile(Long teacherId, Long fileId) {
        FileMetadata metadata = getOwnedActiveFile(teacherId, fileId);
        return toDownload(metadata);
    }

    public AuthorizedFileDownload readFile(CurrentUser actor, Long fileId, AccessPermission permission) {
        FileMetadata metadata = getActiveFile(fileId);
        ResourceAuthorizationService.AccessDecision decision = null;
        if (!actor.teacherId().equals(metadata.getTeacherId())
                && !"ENCRYPTED".equals(metadata.getCiphertextStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Attachment has not completed secure migration");
        }
        if (metadata.getBusinessId() == null) {
            if (!actor.teacherId().equals(metadata.getTeacherId())) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "File not found");
            }
        } else {
            ResourceType type = ResourceType.fromFileBusinessType(metadata.getBusinessType());
            if (permission == AccessPermission.VIEW) {
                authorization.require(actor, type, metadata.getBusinessId(), AccessPermission.VIEW);
            } else {
                authorization.requireOwnerViewEnabled(actor, type, metadata.getBusinessId());
            }
            decision = authorization.require(actor, ResourceType.FILE_ATTACHMENT, metadata.getId(), permission);
        }
        return new AuthorizedFileDownload(toDownload(metadata), decision);
    }

    public FileAuditTarget findAuditTarget(Long fileId) {
        FileMetadata metadata = fileMetadataMapper.selectById(fileId);
        if (metadata == null
                || Boolean.TRUE.equals(metadata.getDeleted())
                || FileBusinessType.AVATAR.name().equals(metadata.getBusinessType())) {
            return null;
        }
        ResourceType type = metadata.getBusinessId() == null
                ? null : ResourceType.fromFileBusinessType(metadata.getBusinessType());
        return new FileAuditTarget(
                metadata.getTeacherId(), type, metadata.getBusinessId(), metadata.getId());
    }

    public record FileAuditTarget(
            Long ownerTeacherId,
            ResourceType resourceType,
            Long resourceId,
            Long fileId) {
    }

    private boolean canReadAttachment(
            CurrentUser actor, FileMetadata metadata, AccessPermission permission) {
        try {
            authorization.require(actor, ResourceType.FILE_ATTACHMENT, metadata.getId(), permission);
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    public record AuthorizedFileDownload(
            FileDownload download,
            ResourceAuthorizationService.AccessDecision decision) {
    }

    public FileMetadata getOwnedFileMetadata(Long teacherId, Long fileId) {
        return getOwnedActiveFile(teacherId, fileId);
    }

    public FileDownload readActiveAiWordTemplate(Long teacherId, Long fileId) {
        return readAiWordTemplate(teacherId, fileId, false);
    }

    public FileDownload readReferencedAiWordTemplate(Long teacherId, Long fileId) {
        return readAiWordTemplate(teacherId, fileId, true);
    }

    public FileMetadata getReferencedAiWordTemplateMetadata(Long teacherId, Long fileId) {
        return getOwnedAiWordTemplateMetadata(teacherId, fileId, true);
    }

    public Path resolveOwnedFilePath(Long teacherId, Long fileId) {
        FileMetadata metadata = getOwnedActiveFile(teacherId, fileId);
        Path filePath = resolveStoragePath(metadata.getStoragePath());
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "File content not found");
        }
        return filePath;
    }

    public <T> T withReadableTempFile(Long teacherId, Long fileId, Function<Path, T> action) {
        FileMetadata metadata = getOwnedActiveFile(teacherId, fileId);
        if (!"ENCRYPTED".equals(metadata.getCiphertextStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Attachment has not completed secure migration");
        }
        String extension = safeReadableTempExtension(metadata.getFileExt());
        Path tempRoot = Path.of(properties.getTempDir()).toAbsolutePath().normalize();
        Path temp = null;
        try {
            Files.createDirectories(tempRoot);
            restrictDirectory(tempRoot);
            temp = Files.createTempFile(
                    tempRoot, ApplicationTempCleaner.DECRYPTED_PREFIX, "." + extension);
            restrictFile(temp);
            try (InputStream input = encryptedFiles.decrypt(
                    resolveStoragePath(metadata.getStoragePath()), metadata);
                    var output = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) {
                input.transferTo(output);
            }
            return action.apply(temp);
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to prepare attachment");
        } finally {
            deleteStoredFile(temp);
        }
    }

    private String safeReadableTempExtension(String value) {
        String extension = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!READABLE_TEMP_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Unsupported OCR file type");
        }
        return extension;
    }

    public FileDownload downloadPublicAvatar(Long fileId) {
        FileMetadata metadata = fileMetadataMapper.selectById(fileId);
        if (metadata == null || Boolean.TRUE.equals(metadata.getDeleted())
                || !FileBusinessType.AVATAR.name().equals(metadata.getBusinessType())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Avatar not found");
        }

        TeacherProfile profile = teacherProfileService.getByTeacherId(metadata.getTeacherId());
        if (!Boolean.TRUE.equals(profile.getPublicEnabled())
                || !fileId.equals(profile.getAvatarFileId())
                || !metadata.getTeacherId().equals(profile.getId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Avatar not found");
        }

        return toDownload(metadata);
    }

    private FileDownload toDownload(FileMetadata metadata) {
        Path filePath = resolveStoragePath(metadata.getStoragePath());
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "File content not found");
        }
        if ("ENCRYPTED".equals(metadata.getCiphertextStatus())) {
            Resource resource = new AbstractResource() {
                @Override
                public String getDescription() {
                    return "encrypted attachment";
                }

                @Override
                public InputStream getInputStream() {
                    return encryptedFiles.decrypt(filePath, metadata);
                }
            };
            return new FileDownload(metadata, resource);
        }
        try {
            return new FileDownload(metadata, new UrlResource(filePath.toUri()));
        } catch (MalformedURLException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
    }

    @Transactional
    public void deleteFile(Long teacherId, Long fileId) {
        FileMetadata metadata = getOwnedActiveFile(teacherId, fileId);
        metadata.setDeleted(Boolean.TRUE);
        fileMetadataMapper.updateById(metadata);
        if (FileBusinessType.AVATAR.name().equals(metadata.getBusinessType())) {
            teacherProfileService.clearAvatarFileId(teacherId, fileId);
        }
    }

    private FileMetadata getOwnedActiveFile(Long teacherId, Long fileId) {
        FileMetadata metadata = getActiveFile(fileId);
        if (metadata == null || !teacherId.equals(metadata.getTeacherId()) || Boolean.TRUE.equals(metadata.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "File not found");
        }
        return metadata;
    }

    private FileMetadata getActiveFile(Long fileId) {
        FileMetadata metadata = fileMetadataMapper.selectById(fileId);
        if (metadata == null || Boolean.TRUE.equals(metadata.getDeleted())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "File not found");
        }
        return metadata;
    }

    private void validateBusinessOwnership(Long teacherId, FileBusinessType businessType, Long businessId) {
        switch (businessType) {
            case AVATAR -> {
                if (businessId != null) {
                    throw new BusinessException("Avatar upload does not accept businessId");
                }
                teacherProfileService.getByTeacherId(teacherId);
            }
            case PAPER -> {
                Paper paper = paperMapper.selectById(requiredBusinessId(businessId));
                if (paper == null || !teacherId.equals(paper.getTeacherId())) {
                    throw new BusinessException(HttpStatus.NOT_FOUND, "Paper not found");
                }
            }
            case PATENT -> {
                Patent patent = patentMapper.selectById(requiredBusinessId(businessId));
                if (patent == null || !teacherId.equals(patent.getTeacherId())) {
                    throw new BusinessException(HttpStatus.NOT_FOUND, "Patent not found");
                }
            }
            case CERTIFICATE -> {
                Certificate certificate = certificateMapper.selectById(requiredBusinessId(businessId));
                if (certificate == null || !teacherId.equals(certificate.getTeacherId())) {
                    throw new BusinessException(HttpStatus.NOT_FOUND, "Certificate not found");
                }
            }
            case AI_WORD_TEMPLATE -> {
                if (businessId != null) {
                    throw new BusinessException("AI Word template upload does not accept businessId");
                }
                teacherProfileService.getByTeacherId(teacherId);
            }
        }
    }

    private Long requiredBusinessId(Long businessId) {
        if (businessId == null) {
            throw new BusinessException("businessId is required");
        }
        return businessId;
    }

    private void validateFile(MultipartFile file, FileBusinessType businessType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is required");
        }
        if (file.getSize() > properties.getMaxUploadSize().toBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds limit");
        }

        String originalName = safeOriginalName(file.getOriginalFilename());
        String fileExt = extensionOf(originalName);
        Map<String, Set<String>> allowedTypes = switch (businessType) {
            case AVATAR -> IMAGE_ALLOWED_MIME_TYPES;
            case PAPER, PATENT, CERTIFICATE -> ATTACHMENT_ALLOWED_MIME_TYPES;
            case AI_WORD_TEMPLATE -> AI_WORD_TEMPLATE_ALLOWED_MIME_TYPES;
        };
        Set<String> allowedMimeTypes = allowedTypes.get(fileExt);
        String contentType = file.getContentType();
        if (allowedMimeTypes == null || !StringUtils.hasText(contentType)
                || !allowedMimeTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("Unsupported file type");
        }
        if (businessType == FileBusinessType.AI_WORD_TEMPLATE) {
            try {
                wordTemplateService.inspect(file.getInputStream());
            } catch (IOException ex) {
                throw new BusinessException("Failed to read Word template upload");
            }
        }
    }

    private FileDownload readAiWordTemplate(Long teacherId, Long fileId, boolean includeDeleted) {
        FileMetadata metadata = getOwnedAiWordTemplateMetadata(teacherId, fileId, includeDeleted);
        return toDownload(metadata);
    }

    private FileMetadata getOwnedAiWordTemplateMetadata(Long teacherId, Long fileId, boolean includeDeleted) {
        FileMetadata metadata = fileMetadataMapper.selectById(fileId);
        if (metadata == null
                || !teacherId.equals(metadata.getTeacherId())
                || !FileBusinessType.AI_WORD_TEMPLATE.name().equals(metadata.getBusinessType())
                || (!includeDeleted && Boolean.TRUE.equals(metadata.getDeleted()))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AI Word template not found");
        }
        return metadata;
    }

    private void deleteStoredFile(Path targetPath) {
        if (targetPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
        }
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void restrictFile(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Platform ACLs apply when POSIX permissions are unavailable.
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

    private String safeOriginalName(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
            throw new BusinessException("Invalid file name");
        }
        return cleaned;
    }

    private String extensionOf(String originalName) {
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            throw new BusinessException("File extension is required");
        }
        return originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private Path relativePath(Long teacherId, FileBusinessType businessType, String storedName) {
        LocalDate today = LocalDate.now();
        return Paths.get(
                teacherId.toString(),
                businessType.name().toLowerCase(Locale.ROOT),
                Integer.toString(today.getYear()),
                String.format("%02d", today.getMonthValue()),
                String.format("%02d", today.getDayOfMonth()),
                storedName);
    }

    private Path resolveStoragePath(String storagePath) {
        Path root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Path resolved = root.resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("Invalid file path");
        }
        return resolved;
    }

    private String toStoragePath(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }
}
