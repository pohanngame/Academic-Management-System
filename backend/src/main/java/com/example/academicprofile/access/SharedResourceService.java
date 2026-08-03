package com.example.academicprofile.access;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.user.UserRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SharedResourceService {
    private static final Set<String> HIDDEN = Set.of(
            "userId", "teacherId", "createdAt", "updatedAt", "sortOrder", "isPublic");
    private final TeacherDirectoryMapper directory;
    private final ResourceCatalog catalog;
    private final ResourceAuthorizationService authorization;
    private final ResourceAccessAuditService audits;
    private final ObjectMapper json;
    private final ResourceAccessPolicyMapper policies;

    public SharedResourceService(
            TeacherDirectoryMapper directory,
            ResourceCatalog catalog,
            ResourceAuthorizationService authorization,
            ResourceAccessAuditService audits,
            ObjectMapper json,
            ResourceAccessPolicyMapper policies) {
        this.directory = directory;
        this.catalog = catalog;
        this.authorization = authorization;
        this.audits = audits;
        this.json = json;
        this.policies = policies;
    }

    public PageResult<TeacherDirectoryMapper.TeacherDirectoryItem> directory(
            CurrentUser actor, String search, String scope, int page, int size) {
        validatePage(page, size);
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SUPER_ADMIN cannot browse private teacher data");
        }
        String normalizedSearch = search == null ? null : search.trim();
        if ("MANAGED".equals(scope)) {
            return new PageResult<>(
                    directory.listManaged(
                            actor.userId(), actor.teacherId(), normalizedSearch,
                            (page - 1) * size, size),
                    page, size,
                    directory.countManaged(actor.userId(), actor.teacherId(), normalizedSearch));
        }
        if (scope != null && !"ALL".equals(scope)) {
            throw new BusinessException("Invalid directory scope");
        }
        return new PageResult<>(
                directory.list(normalizedSearch, (page - 1) * size, size),
                page, size, directory.count(normalizedSearch));
    }

    public PageResult<Map<String, Object>> list(
            CurrentUser actor, Long ownerTeacherId, ResourceType type, int page, int size) {
        validatePage(page, size);
        if (!ownerTeacherId.equals(actor.teacherId())
                && !authorization.inManagementScope(actor, ownerTeacherId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Resources not found");
        }
        boolean owner = ownerTeacherId.equals(actor.teacherId());
        Page<ResourceAccessPolicy> policyPage = policies.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<ResourceAccessPolicy>()
                        .eq(ResourceAccessPolicy::getOwnerTeacherId, ownerTeacherId)
                        .eq(ResourceAccessPolicy::getResourceType, type.name())
                        .eq(!owner, ResourceAccessPolicy::getViewAllowed, Boolean.TRUE)
                        .orderByDesc(ResourceAccessPolicy::getResourceId));
        List<Long> ids = policyPage.getRecords().stream()
                .map(ResourceAccessPolicy::getResourceId)
                .toList();
        Map<Long, Object> values = new LinkedHashMap<>();
        for (Object value : catalog.listByIds(type, ids)) {
            Map<String, Object> map = json.convertValue(value, new TypeReference<>() {});
            Long id = ((Number) map.get("id")).longValue();
            values.put(id, value);
        }
        List<Map<String, Object>> allowed = new ArrayList<>();
        for (Long id : ids) {
            Object value = values.get(id);
            if (value == null) continue;
            Map<String, Object> map = json.convertValue(value, new TypeReference<>() {});
            allowed.add(sanitize(type, value));
        }
        return new PageResult<>(allowed, page, size, policyPage.getTotal());
    }

    public Map<String, Object> detail(CurrentUser actor, ResourceType type, Long id) {
        var decision = authorization.require(actor, type, id, AccessPermission.VIEW);
        Object value = catalog.get(type, id);
        Map<String, Object> result = sanitize(type, value);
        audits.record(actor, decision.ownerTeacherId(), "DETAIL", type, id,
                type == ResourceType.FILE_ATTACHMENT ? id : null,
                decision.grant(), true, null, "Resource detail");
        return result;
    }

    private Map<String, Object> sanitize(ResourceType type, Object value) {
        if (type == ResourceType.FILE_ATTACHMENT) {
            return json.convertValue(
                    AccessDtos.SharedAttachmentResponse.from((FileMetadata) value),
                    new TypeReference<>() {});
        }
        Map<String, Object> source = json.convertValue(value, new TypeReference<>() {});
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.keySet().removeIf(HIDDEN::contains);
        return result;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException("Invalid pagination");
    }

    public record PageResult<T>(List<T> items, int page, int size, long total) {
    }
}
