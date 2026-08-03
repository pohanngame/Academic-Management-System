package com.example.academicprofile.access;

import java.time.LocalDateTime;
import java.util.List;

import com.example.academicprofile.file.FileMetadata;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AccessDtos {
    private AccessDtos() {
    }

    public record CreateRequest(
            @NotEmpty @Size(max = 50) List<Long> ownerTeacherIds,
            @NotBlank String resourceType,
            @NotBlank String permissionType,
            @NotBlank @Size(max = 1000) String purpose,
            @NotNull @Future LocalDateTime requestedExpiresAt,
            @Size(max = 100) List<ResourceRef> resources) {
    }

    public record DecisionRequest(
            boolean approve,
            @Size(max = 1000) String comment,
            @Size(max = 100) List<ResourceRef> approvedResources) {
    }

    public record ResourceRef(
            @NotBlank String resourceType,
            @NotNull Long resourceId) {
    }

    public record SharedAttachmentResponse(
            Long id,
            String originalName,
            String fileExt,
            String mimeType,
            Long fileSize,
            String businessType,
            Long businessId,
            LocalDateTime createdAt) {
        static SharedAttachmentResponse from(FileMetadata value) {
            return new SharedAttachmentResponse(
                    value.getId(),
                    value.getOriginalName(),
                    value.getFileExt(),
                    value.getMimeType(),
                    value.getFileSize(),
                    value.getBusinessType(),
                    value.getBusinessId(),
                    value.getCreatedAt());
        }
    }

    public record AccessCapabilitiesResponse(
            List<String> businessRoles,
            Long currentMajorId,
            String currentMajorName,
            List<Long> managedMajorIds,
            boolean canBatchRequest,
            boolean canBrowseManagedScope) {
    }

    public record ApprovalCandidateResponse(
            String resourceType,
            Long resourceId,
            String displayName,
            boolean allowed,
            String unavailableReason) {
    }

    public record PolicyBatchRequest(
            @NotEmpty @Size(max = 100) List<ResourcePolicyService.PolicyUpdate> items) {
    }

    public record AccessRequestResponse(
            Long id,
            Long requesterTeacherId,
            Long ownerTeacherId,
            String resourceType,
            String permissionType,
            String purpose,
            String status,
            LocalDateTime requestedExpiresAt,
            LocalDateTime decidedAt,
            String decisionComment,
            LocalDateTime createdAt) {
        static AccessRequestResponse from(ResourceAccessRequest value) {
            return new AccessRequestResponse(
                    value.getId(), value.getRequesterTeacherId(), value.getOwnerTeacherId(),
                    value.getResourceType(), value.getPermissionType(), value.getPurpose(),
                    value.getStatus(), value.getRequestedExpiresAt(), value.getDecidedAt(),
                    value.getDecisionComment(), value.getCreatedAt());
        }
    }

    public record GrantResponse(
            Long id,
            Long requestId,
            Long requesterTeacherId,
            Long ownerTeacherId,
            String permissionType,
            String resourceType,
            Long resourceId,
            String resourceName,
            String resourceStatus,
            String purpose,
            LocalDateTime effectiveAt,
            LocalDateTime expiresAt,
            LocalDateTime revokedAt) {
        static GrantResponse from(
                ResourceAccessGrant value, String resourceName, String resourceStatus) {
            return new GrantResponse(
                    value.getId(), value.getRequestId(), value.getRequesterTeacherId(),
                    value.getOwnerTeacherId(), value.getPermissionType(), value.getResourceType(),
                    value.getResourceId(), resourceName, resourceStatus,
                    value.getPurpose(), value.getEffectiveAt(),
                    value.getExpiresAt(), value.getRevokedAt());
        }
    }

    public record AccessAuditResponse(
            Long id,
            Long actorTeacherId,
            String actorDisplayName,
            Long ownerTeacherId,
            String ownerDisplayName,
            String operationType,
            String resourceType,
            Long resourceId,
            Long fileId,
            String resourceName,
            String purpose,
            Long requestId,
            Long grantId,
            boolean success,
            LocalDateTime createdAt) {
        static AccessAuditResponse from(
                ResourceAccessAudit value,
                String actorDisplayName,
                String ownerDisplayName,
                String resourceName) {
            return new AccessAuditResponse(
                    value.getId(), value.getActorTeacherId(), actorDisplayName,
                    value.getOwnerTeacherId(), ownerDisplayName,
                    value.getOperationType(), value.getResourceType(), value.getResourceId(),
                    value.getFileId(), resourceName, value.getPurpose(),
                    value.getRequestId(), value.getGrantId(),
                    Boolean.TRUE.equals(value.getSuccess()), value.getCreatedAt());
        }
    }
}
