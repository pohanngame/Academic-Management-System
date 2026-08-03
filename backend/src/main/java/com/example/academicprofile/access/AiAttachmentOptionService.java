package com.example.academicprofile.access;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileBusinessType;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.file.FileMetadataMapper;
import com.example.academicprofile.ocr.OcrTask;
import com.example.academicprofile.ocr.OcrTaskMapper;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserRole;

@Service
public class AiAttachmentOptionService {
    private final FileMetadataMapper files;
    private final ResourceAccessGrantMapper grants;
    private final OcrTaskMapper ocrTasks;
    private final TeacherProfileService teachers;

    public AiAttachmentOptionService(
            FileMetadataMapper files,
            ResourceAccessGrantMapper grants,
            OcrTaskMapper ocrTasks,
            TeacherProfileService teachers) {
        this.files = files;
        this.grants = grants;
        this.ocrTasks = ocrTasks;
        this.teachers = teachers;
    }

    public List<AiAttachmentOption> list(CurrentUser actor) {
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SUPER_ADMIN cannot access private attachments");
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> grantedIds = grants.selectList(new LambdaQueryWrapper<ResourceAccessGrant>()
                        .eq(ResourceAccessGrant::getRequesterTeacherId, actor.teacherId())
                        .eq(ResourceAccessGrant::getResourceType, ResourceType.FILE_ATTACHMENT.name())
                        .eq(ResourceAccessGrant::getPermissionType, AccessPermission.AI_USE.name())
                        .isNull(ResourceAccessGrant::getRevokedAt)
                        .le(ResourceAccessGrant::getEffectiveAt, now)
                        .gt(ResourceAccessGrant::getExpiresAt, now))
                .stream()
                .map(ResourceAccessGrant::getResourceId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<FileMetadata> available = files.selectList(new LambdaQueryWrapper<FileMetadata>()
                .and(query -> query.eq(FileMetadata::getTeacherId, actor.teacherId())
                        .or(!grantedIds.isEmpty(), nested -> nested.in(FileMetadata::getId, grantedIds)))
                .ne(FileMetadata::getBusinessType, FileBusinessType.AVATAR.name())
                .eq(FileMetadata::getDeleted, Boolean.FALSE)
                .orderByDesc(FileMetadata::getId)
                .last("LIMIT 100"));
        Set<Long> fileIds = available.stream().map(FileMetadata::getId).collect(
                java.util.stream.Collectors.toSet());
        Set<Long> confirmed = fileIds.isEmpty() ? Set.of() : ocrTasks.selectList(
                        new LambdaQueryWrapper<OcrTask>()
                                .in(OcrTask::getFileId, fileIds)
                                .eq(OcrTask::getStatus, "CONFIRMED"))
                .stream().map(OcrTask::getFileId).collect(java.util.stream.Collectors.toSet());
        return available.stream().map(file -> new AiAttachmentOption(
                file.getId(),
                file.getOriginalName(),
                file.getTeacherId(),
                teachers.getByTeacherId(file.getTeacherId()).getDisplayName(),
                file.getBusinessType(),
                file.getBusinessId(),
                confirmed.contains(file.getId())))
                .toList();
    }

    public record AiAttachmentOption(
            Long id,
            String fileName,
            Long ownerTeacherId,
            String ownerDisplayName,
            String resourceType,
            Long resourceId,
            boolean ocrConfirmed) {
    }
}
