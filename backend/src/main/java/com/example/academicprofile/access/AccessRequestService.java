package com.example.academicprofile.access;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.academicprofile.access.AccessDtos.AccessRequestResponse;
import com.example.academicprofile.access.AccessDtos.CreateRequest;
import com.example.academicprofile.access.AccessDtos.DecisionRequest;
import com.example.academicprofile.access.AccessDtos.GrantResponse;
import com.example.academicprofile.access.AccessDtos.ApprovalCandidateResponse;
import com.example.academicprofile.access.AccessDtos.ResourceRef;
import com.example.academicprofile.file.FileMetadata;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserRole;

@Service
public class AccessRequestService {
    private static final int MAX_DAYS = 365;
    private final ResourceAccessRequestMapper requests;
    private final ResourceAccessRequestItemMapper items;
    private final ResourceAccessGrantMapper grants;
    private final ResourceCatalog catalog;
    private final ResourceAuthorizationService authorization;
    private final ResourceAccessPolicyMapper policies;
    private final TeacherProfileService teachers;
    private final ResourceAccessAuditService audits;

    public AccessRequestService(
            ResourceAccessRequestMapper requests,
            ResourceAccessRequestItemMapper items,
            ResourceAccessGrantMapper grants,
            ResourceCatalog catalog,
            ResourceAuthorizationService authorization,
            ResourceAccessPolicyMapper policies,
            TeacherProfileService teachers,
            ResourceAccessAuditService audits) {
        this.requests = requests;
        this.items = items;
        this.grants = grants;
        this.catalog = catalog;
        this.authorization = authorization;
        this.policies = policies;
        this.teachers = teachers;
        this.audits = audits;
    }

    @Transactional
    public List<AccessRequestResponse> create(CurrentUser actor, CreateRequest input) {
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SUPER_ADMIN cannot request private teacher data");
        }
        ResourceType type = ResourceType.require(input.resourceType());
        AccessPermission permission = AccessPermission.require(input.permissionType());
        validateExpiry(input.requestedExpiresAt());
        Set<Long> owners = new LinkedHashSet<>(input.ownerTeacherIds());
        if (owners.size() != input.ownerTeacherIds().size() || owners.contains(actor.teacherId())) {
            throw new BusinessException("Invalid or duplicate resource owner");
        }
        if (owners.size() > 1 && owners.stream().anyMatch(owner -> !authorization.inManagementScope(actor, owner))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Batch requests require management scope for every owner");
        }
        List<ResourceRef> resources = input.resources() == null
                ? List.of()
                : new LinkedHashSet<>(input.resources()).stream().toList();
        if (owners.size() > 1 && !resources.isEmpty()) {
            throw new BusinessException("Batch requests across owners must be made by resource category");
        }
        List<ResourceAccessRequest> created = owners.stream().map(owner -> createOne(
                actor.teacherId(), owner, type, permission, input.purpose(),
                input.requestedExpiresAt(), resources)).toList();
        created.forEach(request -> audits.recordAction(
                actor, request.getOwnerTeacherId(), "REQUEST_CREATE", type,
                null, request.getId(), null, true));
        return created.stream().map(AccessRequestResponse::from).toList();
    }

    private ResourceAccessRequest createOne(
            Long requester, Long owner, ResourceType type, AccessPermission permission,
            String purpose, LocalDateTime expiresAt, List<ResourceRef> resources) {
        teachers.getByTeacherId(owner);
        ResourceAccessRequest request = new ResourceAccessRequest();
        request.setRequesterTeacherId(requester);
        request.setOwnerTeacherId(owner);
        request.setResourceType(type.name());
        request.setPermissionType(permission.name());
        request.setPurpose(purpose.trim());
        request.setStatus("PENDING");
        request.setRequestedExpiresAt(expiresAt);
        requests.insert(request);
        for (ResourceRef ref : resources) {
            ResourceType itemType = ResourceType.require(ref.resourceType());
            requireWithinCategory(type, itemType, ref.resourceId());
            requireRequestable(owner, itemType, ref.resourceId(), permission);
            ResourceAccessRequestItem item = new ResourceAccessRequestItem();
            item.setRequestId(request.getId());
            item.setResourceType(itemType.name());
            item.setResourceId(ref.resourceId());
            items.insert(item);
        }
        return request;
    }

    public Page<AccessRequestResponse> myRequests(Long teacherId, int page, int size) {
        validatePage(page, size);
        return map(requests.selectPage(Page.of(page, size), new LambdaQueryWrapper<ResourceAccessRequest>()
                .eq(ResourceAccessRequest::getRequesterTeacherId, teacherId)
                .orderByDesc(ResourceAccessRequest::getCreatedAt)));
    }

    public Page<AccessRequestResponse> pendingForOwner(Long teacherId, int page, int size) {
        validatePage(page, size);
        return map(requests.selectPage(Page.of(page, size), new LambdaQueryWrapper<ResourceAccessRequest>()
                .eq(ResourceAccessRequest::getOwnerTeacherId, teacherId)
                .eq(ResourceAccessRequest::getStatus, "PENDING")
                .orderByAsc(ResourceAccessRequest::getCreatedAt)));
    }

    public List<ApprovalCandidateResponse> approvalCandidates(CurrentUser actor, Long requestId) {
        ResourceAccessRequest request = requests.selectById(requestId);
        if (request == null || !actor.teacherId().equals(request.getOwnerTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Access request not found");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Access request has already been decided");
        }
        ResourceType requestedType = ResourceType.require(request.getResourceType());
        AccessPermission permission = AccessPermission.require(request.getPermissionType());
        List<ResourceAccessRequestItem> requestedItems = items.selectList(
                new LambdaQueryWrapper<ResourceAccessRequestItem>()
                        .eq(ResourceAccessRequestItem::getRequestId, requestId));
        Set<ResourceRef> requestedRefs = requestedItems.stream()
                .map(item -> new ResourceRef(item.getResourceType(), item.getResourceId()))
                .collect(Collectors.toSet());

        List<Object> resources = new ArrayList<>();
        resources.addAll(catalog.list(requestedType, request.getOwnerTeacherId()));
        if (requestedType == ResourceType.PAPER
                || requestedType == ResourceType.PATENT
                || requestedType == ResourceType.CERTIFICATE) {
            resources.addAll(catalog.list(ResourceType.FILE_ATTACHMENT, request.getOwnerTeacherId())
                    .stream()
                    .filter(FileMetadata.class::isInstance)
                    .map(FileMetadata.class::cast)
                    .filter(file -> file.getBusinessId() != null
                            && requestedType == ResourceType.fromFileBusinessType(file.getBusinessType()))
                    .toList());
        }

        Map<String, ResourceAccessPolicy> policyByRef = policies.selectList(
                new LambdaQueryWrapper<ResourceAccessPolicy>()
                        .eq(ResourceAccessPolicy::getOwnerTeacherId, request.getOwnerTeacherId())
                        .in(ResourceAccessPolicy::getResourceType,
                                List.of(requestedType.name(), ResourceType.FILE_ATTACHMENT.name())))
                .stream()
                .collect(Collectors.toMap(
                        policy -> policy.getResourceType() + ":" + policy.getResourceId(),
                        Function.identity(),
                        (left, right) -> left));

        return resources.stream().map(value -> {
            ResourceType type = value instanceof FileMetadata
                    ? ResourceType.FILE_ATTACHMENT : requestedType;
            Long id = catalog.idOf(value);
            ResourceRef ref = new ResourceRef(type.name(), id);
            ResourceAccessPolicy policy = policyByRef.get(type.name() + ":" + id);
            boolean inRequest = requestedRefs.isEmpty() || requestedRefs.contains(ref);
            boolean allowed = inRequest && isAllowed(policy, permission);
            String reason = allowed ? null : inRequest
                    ? "所有人尚未开放该权限" : "不在本次申请范围内";
            return new ApprovalCandidateResponse(
                    type.name(), id, catalog.safeDisplayName(type, value), allowed, reason);
        }).toList();
    }

    @Transactional
    public AccessRequestResponse decide(CurrentUser actor, Long requestId, DecisionRequest decision) {
        Long ownerTeacherId = actor.teacherId();
        ResourceAccessRequest request = requests.selectByIdForUpdate(requestId);
        if (request == null || !ownerTeacherId.equals(request.getOwnerTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Access request not found");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Access request has already been decided");
        }
        request.setDecisionComment(trimToNull(decision.comment()));
        request.setDecidedAt(LocalDateTime.now());
        if (!decision.approve()) {
            request.setStatus("REJECTED");
            requests.updateById(request);
            audits.recordAction(actor, ownerTeacherId, "REQUEST_REJECT", typeOf(request),
                    null, requestId, null, true);
            return AccessRequestResponse.from(request);
        }
        validateExpiry(request.getRequestedExpiresAt());
        ResourceType type = ResourceType.require(request.getResourceType());
        AccessPermission permission = AccessPermission.require(request.getPermissionType());
        List<ResourceRef> approved = decision.approvedResources() == null
                ? List.of()
                : new LinkedHashSet<>(decision.approvedResources()).stream().toList();
        if (approved.isEmpty()) {
            throw new BusinessException("At least one resource must be approved");
        }
        List<ResourceAccessRequestItem> requestedItems = items.selectList(
                new LambdaQueryWrapper<ResourceAccessRequestItem>()
                        .eq(ResourceAccessRequestItem::getRequestId, requestId));
        Set<ResourceRef> requestedRefs = requestedItems.stream()
                .map(item -> new ResourceRef(item.getResourceType(), item.getResourceId()))
                .collect(java.util.stream.Collectors.toSet());
        if (!requestedRefs.isEmpty() && !requestedRefs.containsAll(approved)) {
            throw new BusinessException("Approved resources must be within the request");
        }
        for (ResourceRef ref : approved) {
            ResourceType approvedType = ResourceType.require(ref.resourceType());
            requireWithinCategory(type, approvedType, ref.resourceId());
            requireRequestable(ownerTeacherId, approvedType, ref.resourceId(), permission);
            ResourceAccessGrant grant = new ResourceAccessGrant();
            grant.setRequestId(requestId);
            grant.setRequesterTeacherId(request.getRequesterTeacherId());
            grant.setOwnerTeacherId(ownerTeacherId);
            grant.setPermissionType(permission.name());
            grant.setResourceType(approvedType.name());
            grant.setResourceId(ref.resourceId());
            grant.setPurpose(request.getPurpose());
            grant.setEffectiveAt(LocalDateTime.now().withNano(0));
            grant.setExpiresAt(request.getRequestedExpiresAt());
            grants.insert(grant);
        }
        requestedItems.forEach(item -> {
            item.setApproved(approved.contains(new ResourceRef(item.getResourceType(), item.getResourceId())));
            items.updateById(item);
        });
        request.setStatus("APPROVED");
        requests.updateById(request);
        audits.recordAction(actor, ownerTeacherId, "REQUEST_APPROVE", type,
                null, requestId, null, true);
        return AccessRequestResponse.from(request);
    }

    @Transactional
    public void cancel(CurrentUser actor, Long requestId) {
        Long requesterTeacherId = actor.teacherId();
        ResourceAccessRequest request = requests.selectByIdForUpdate(requestId);
        if (request == null || !requesterTeacherId.equals(request.getRequesterTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Access request not found");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Only pending requests can be cancelled");
        }
        request.setStatus("CANCELLED");
        request.setDecidedAt(LocalDateTime.now());
        requests.updateById(request);
        audits.recordAction(actor, request.getOwnerTeacherId(), "REQUEST_CANCEL",
                typeOf(request), null, requestId, null, true);
    }

    public Page<GrantResponse> grants(Long teacherId, String state, int page, int size) {
        validatePage(page, size);
        LambdaQueryWrapper<ResourceAccessGrant> query = new LambdaQueryWrapper<ResourceAccessGrant>()
                .and(wrapper -> wrapper.eq(ResourceAccessGrant::getRequesterTeacherId, teacherId)
                        .or().eq(ResourceAccessGrant::getOwnerTeacherId, teacherId));
        LocalDateTime now = LocalDateTime.now();
        if ("ACTIVE".equals(state)) {
            query.isNull(ResourceAccessGrant::getRevokedAt).gt(ResourceAccessGrant::getExpiresAt, now);
        } else if ("INACTIVE".equals(state)) {
            query.and(wrapper -> wrapper.isNotNull(ResourceAccessGrant::getRevokedAt)
                    .or().le(ResourceAccessGrant::getExpiresAt, now));
        }
        Page<ResourceAccessGrant> result = grants.selectPage(Page.of(page, size),
                query.orderByDesc(ResourceAccessGrant::getCreatedAt));
        Page<GrantResponse> response = Page.of(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(result.getRecords().stream().map(value -> {
            try {
                ResourceType type = ResourceType.require(value.getResourceType());
                catalog.owner(type, value.getResourceId());
                return GrantResponse.from(
                        value, catalog.safeDisplayName(type, value.getResourceId()), "AVAILABLE");
            } catch (RuntimeException ex) {
                return GrantResponse.from(value, "已删除或不可用的资料", "UNAVAILABLE");
            }
        }).toList());
        return response;
    }

    @Transactional
    public void revoke(CurrentUser actor, Long grantId) {
        Long ownerTeacherId = actor.teacherId();
        ResourceAccessGrant grant = grants.selectById(grantId);
        if (grant == null || !ownerTeacherId.equals(grant.getOwnerTeacherId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Access grant not found");
        }
        if (grant.getRevokedAt() == null) {
            grant.setRevokedAt(LocalDateTime.now());
            grant.setRevokedByTeacherId(ownerTeacherId);
            grants.updateById(grant);
            audits.recordAction(actor, ownerTeacherId, "GRANT_REVOKE",
                    ResourceType.require(grant.getResourceType()), grant.getResourceId(),
                    grant.getRequestId(), grantId, true);
        }
    }

    private ResourceType typeOf(ResourceAccessRequest request) {
        return ResourceType.require(request.getResourceType());
    }

    private void requireRequestable(
            Long owner, ResourceType type, Long resourceId, AccessPermission permission) {
        if (!owner.equals(catalog.owner(type, resourceId))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Resource not found");
        }
        ResourceAccessPolicy policy = policies.selectOne(new LambdaQueryWrapper<ResourceAccessPolicy>()
                .eq(ResourceAccessPolicy::getResourceType, type.name())
                .eq(ResourceAccessPolicy::getResourceId, resourceId));
        boolean allowed = policy != null && Boolean.TRUE.equals(policy.getViewAllowed())
                && (permission == AccessPermission.VIEW
                    || permission == AccessPermission.DOWNLOAD && Boolean.TRUE.equals(policy.getDownloadAllowed())
                    || permission == AccessPermission.AI_USE && Boolean.TRUE.equals(policy.getAiUseAllowed()));
        if (!allowed) {
            throw new BusinessException("The owner does not allow this access request");
        }
    }

    private boolean isAllowed(ResourceAccessPolicy policy, AccessPermission permission) {
        return policy != null
                && Boolean.TRUE.equals(policy.getViewAllowed())
                && (permission == AccessPermission.VIEW
                    || permission == AccessPermission.DOWNLOAD
                            && Boolean.TRUE.equals(policy.getDownloadAllowed())
                    || permission == AccessPermission.AI_USE
                            && Boolean.TRUE.equals(policy.getAiUseAllowed()));
    }

    private void requireWithinCategory(
            ResourceType requestedType, ResourceType itemType, Long resourceId) {
        if (itemType == requestedType) {
            return;
        }
        if (itemType != ResourceType.FILE_ATTACHMENT) {
            throw new BusinessException("Requested resource is outside the selected category");
        }
        Object value = catalog.get(itemType, resourceId);
        if (!(value instanceof FileMetadata file)
                || file.getBusinessId() == null
                || ResourceType.fromFileBusinessType(file.getBusinessType()) != requestedType) {
            throw new BusinessException("Requested attachment is outside the selected category");
        }
    }

    private void validateExpiry(LocalDateTime expiresAt) {
        long days = Duration.between(LocalDateTime.now(), expiresAt).toDays();
        if (!expiresAt.isAfter(LocalDateTime.now()) || days >= MAX_DAYS + 1L) {
            throw new BusinessException("Authorization period must be between 1 and 365 days");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("Invalid pagination");
        }
    }

    private Page<AccessRequestResponse> map(Page<ResourceAccessRequest> source) {
        Page<AccessRequestResponse> result = Page.of(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(AccessRequestResponse::from).toList());
        return result;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
