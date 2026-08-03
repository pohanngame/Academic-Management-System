package com.example.academicprofile.access;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.access.AccessDtos.AccessAuditResponse;
import com.example.academicprofile.teacher.TeacherProfileService;

@Service
public class ResourceAccessAuditService {
    private final ResourceAccessAuditMapper mapper;
    private final TeacherProfileService teachers;
    private final ResourceCatalog catalog;

    public ResourceAccessAuditService(
            ResourceAccessAuditMapper mapper,
            TeacherProfileService teachers,
            ResourceCatalog catalog) {
        this.mapper = mapper;
        this.teachers = teachers;
        this.catalog = catalog;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            CurrentUser actor,
            Long ownerTeacherId,
            String operation,
            ResourceType type,
            Long resourceId,
            Long fileId,
            ResourceAccessGrant grant,
            boolean success,
            String ip,
            String clientInfo) {
        ResourceAccessAudit audit = new ResourceAccessAudit();
        audit.setActorUserId(actor.userId());
        audit.setActorTeacherId(actor.teacherId());
        audit.setOwnerTeacherId(ownerTeacherId);
        audit.setOperationType(operation);
        audit.setResourceType(type == null ? null : type.name());
        audit.setResourceId(resourceId);
        audit.setFileId(fileId);
        audit.setRequestId(grant == null ? null : grant.getRequestId());
        audit.setGrantId(grant == null ? null : grant.getId());
        audit.setPurpose(grant == null ? null : limit(grant.getPurpose(), 1000));
        audit.setSuccess(success);
        audit.setIpAddress(limit(ip, 64));
        audit.setClientInfo(limit(clientInfo, 255));
        mapper.insert(audit);
    }

    @Transactional
    public void recordAction(
            CurrentUser actor,
            Long ownerTeacherId,
            String operation,
            ResourceType type,
            Long resourceId,
            Long requestId,
            Long grantId,
            boolean success) {
        ResourceAccessAudit audit = action(
                actor, ownerTeacherId, operation, type, resourceId, requestId, grantId, success);
        mapper.insert(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryRecordFailure(
            CurrentUser actor,
            Long ownerTeacherId,
            String operation,
            ResourceType type,
            Long resourceId,
            Long requestId,
            Long grantId) {
        try {
            mapper.insert(action(
                    actor, ownerTeacherId, operation, type, resourceId, requestId, grantId, false));
        } catch (RuntimeException ignored) {
            // Failure auditing must never replace the controlled business exception.
        }
    }

    private ResourceAccessAudit action(
            CurrentUser actor,
            Long ownerTeacherId,
            String operation,
            ResourceType type,
            Long resourceId,
            Long requestId,
            Long grantId,
            boolean success) {
        ResourceAccessAudit audit = new ResourceAccessAudit();
        audit.setActorUserId(actor.userId());
        audit.setActorTeacherId(actor.teacherId());
        audit.setOwnerTeacherId(ownerTeacherId);
        audit.setOperationType(operation);
        audit.setResourceType(type == null ? null : type.name());
        audit.setResourceId(resourceId);
        audit.setRequestId(requestId);
        audit.setGrantId(grantId);
        audit.setSuccess(success);
        return audit;
    }

    public Page<AccessAuditResponse> pageForOwner(Long ownerTeacherId, long page, long size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new com.example.academicprofile.common.exception.BusinessException("Invalid pagination");
        }
        Page<ResourceAccessAudit> source = mapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<ResourceAccessAudit>()
                .eq(ResourceAccessAudit::getOwnerTeacherId, ownerTeacherId)
                .orderByDesc(ResourceAccessAudit::getCreatedAt));
        Page<AccessAuditResponse> result = Page.of(source.getCurrent(), source.getSize(), source.getTotal());
        Map<Long, String> teacherNames = new HashMap<>();
        result.setRecords(source.getRecords().stream().map(value -> AccessAuditResponse.from(
                value,
                teacherNames.computeIfAbsent(
                        value.getActorTeacherId(), this::teacherDisplayName),
                teacherNames.computeIfAbsent(
                        value.getOwnerTeacherId(), this::teacherDisplayName),
                resourceDisplayName(value))).toList());
        return result;
    }

    private String teacherDisplayName(Long teacherId) {
        try {
            return teachers.getByTeacherId(teacherId).getDisplayName();
        } catch (RuntimeException ex) {
            return "教师 #" + teacherId;
        }
    }

    private String resourceDisplayName(ResourceAccessAudit audit) {
        try {
            if (audit.getFileId() != null) {
                return catalog.safeDisplayName(ResourceType.FILE_ATTACHMENT, audit.getFileId());
            }
            if (audit.getResourceType() != null && audit.getResourceId() != null) {
                return catalog.safeDisplayName(
                        ResourceType.require(audit.getResourceType()), audit.getResourceId());
            }
        } catch (RuntimeException ignored) {
            return "已删除或不可用的资料";
        }
        return null;
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
