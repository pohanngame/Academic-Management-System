package com.example.academicprofile.file;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.achievement.Certificate;
import com.example.academicprofile.achievement.CertificateMapper;
import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.achievement.Patent;
import com.example.academicprofile.achievement.PatentMapper;
import com.example.academicprofile.common.exception.BusinessException;
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

    private final FileMetadataMapper fileMetadataMapper;
    private final FileStorageProperties properties;
    private final TeacherProfileService teacherProfileService;
    private final PaperMapper paperMapper;
    private final PatentMapper patentMapper;
    private final CertificateMapper certificateMapper;
    private final WordTemplateService wordTemplateService;

    public FileStorageService(
            FileMetadataMapper fileMetadataMapper,
            FileStorageProperties properties,
            TeacherProfileService teacherProfileService,
            PaperMapper paperMapper,
            PatentMapper patentMapper,
            CertificateMapper certificateMapper,
            WordTemplateService wordTemplateService) {
        this.fileMetadataMapper = fileMetadataMapper;
        this.properties = properties;
        this.teacherProfileService = teacherProfileService;
        this.paperMapper = paperMapper;
        this.patentMapper = patentMapper;
        this.certificateMapper = certificateMapper;
        this.wordTemplateService = wordTemplateService;
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

    @Transactional
    public FileMetadata uploadFile(Long teacherId, FileBusinessType businessType, Long businessId, MultipartFile file) {
        validateBusinessOwnership(teacherId, businessType, businessId);
        validateFile(file, businessType);

        String originalName = safeOriginalName(file.getOriginalFilename());
        String fileExt = extensionOf(originalName);
        String storedName = UUID.randomUUID() + "." + fileExt;
        Path relativePath = relativePath(teacherId, businessType, storedName);
        Path targetPath = resolveStoragePath(relativePath.toString());

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException ex) {
            deleteStoredFile(targetPath);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
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
        metadata.setDeleted(Boolean.FALSE);
        try {
            fileMetadataMapper.insert(metadata);
        } catch (RuntimeException ex) {
            deleteStoredFile(targetPath);
            throw ex;
        }
        return metadata;
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
        FileMetadata metadata = fileMetadataMapper.selectById(fileId);
        if (metadata == null || !teacherId.equals(metadata.getTeacherId()) || Boolean.TRUE.equals(metadata.getDeleted())) {
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
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
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
