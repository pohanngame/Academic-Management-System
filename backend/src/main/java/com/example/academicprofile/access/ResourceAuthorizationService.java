package com.example.academicprofile.access;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.organization.AcademicRole;
import com.example.academicprofile.organization.OrganizationService;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.user.UserRole;
import com.example.academicprofile.teacher.TeacherProfileService;

@Service
public class ResourceAuthorizationService {
    private final ResourceCatalog catalog;
    private final ResourceAccessPolicyMapper policies;
    private final ResourceAccessGrantMapper grants;
    private final OrganizationService organization;
    private final TeacherProfileService teachers;

    public ResourceAuthorizationService(
            ResourceCatalog catalog,
            ResourceAccessPolicyMapper policies,
            ResourceAccessGrantMapper grants,
            OrganizationService organization,
            TeacherProfileService teachers) {
        this.catalog = catalog;
        this.policies = policies;
        this.grants = grants;
        this.organization = organization;
        this.teachers = teachers;
    }

    public AccessDecision require(
            CurrentUser actor, ResourceType type, Long resourceId, AccessPermission permission) {
        return findAuthorized(actor, type, resourceId, permission).orElseThrow(this::denied);
    }

    public Optional<AccessDecision> findAuthorized(
            CurrentUser actor, ResourceType type, Long resourceId, AccessPermission permission) {
        Long owner = catalog.owner(type, resourceId);
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            return Optional.empty();
        }
        if (owner.equals(actor.teacherId())) {
            return Optional.of(new AccessDecision(owner, null));
        }
        ResourceAccessPolicy policy = policy(type, resourceId, owner);
        if (!Boolean.TRUE.equals(policy.getViewAllowed())) {
            return Optional.empty();
        }
        if (permission == AccessPermission.VIEW && inManagementScope(actor, owner)) {
            return Optional.of(new AccessDecision(owner, null));
        }
        if (permission == AccessPermission.DOWNLOAD && !Boolean.TRUE.equals(policy.getDownloadAllowed())) {
            return Optional.empty();
        }
        if (permission == AccessPermission.AI_USE && !Boolean.TRUE.equals(policy.getAiUseAllowed())) {
            return Optional.empty();
        }
        ResourceAccessGrant grant = grants.selectOne(new LambdaQueryWrapper<ResourceAccessGrant>()
                .eq(ResourceAccessGrant::getRequesterTeacherId, actor.teacherId())
                .eq(ResourceAccessGrant::getOwnerTeacherId, owner)
                .eq(ResourceAccessGrant::getPermissionType, permission.name())
                .eq(ResourceAccessGrant::getResourceType, type.name())
                .eq(ResourceAccessGrant::getResourceId, resourceId)
                .isNull(ResourceAccessGrant::getRevokedAt)
                .le(ResourceAccessGrant::getEffectiveAt, LocalDateTime.now())
                .gt(ResourceAccessGrant::getExpiresAt, LocalDateTime.now())
                .orderByDesc(ResourceAccessGrant::getExpiresAt)
                .last("LIMIT 1"));
        if (grant == null) {
            return Optional.empty();
        }
        return Optional.of(new AccessDecision(owner, grant));
    }

    public boolean inManagementScope(CurrentUser actor, Long ownerTeacherId) {
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            return false;
        }
        Long ownerMajor = organization.activeMajorForTeacher(ownerTeacherId);
        int ownerLevel = organization.rolesForUser(
                        teachers.getByTeacherId(ownerTeacherId).getUserId()).stream()
                .mapToInt(assignment -> level(AcademicRole.require(assignment.getRoleCode())))
                .max().orElse(1);
        return organization.rolesForUser(actor.userId()).stream().anyMatch(assignment -> {
            AcademicRole role = AcademicRole.require(assignment.getRoleCode());
            return switch (role) {
                case DEAN, PARTY_SECRETARY -> ownerLevel <= level(AcademicRole.VICE_DEAN);
                case VICE_DEAN -> ownerLevel <= level(AcademicRole.MAJOR_LEADER);
                case MAJOR_LEADER -> ownerLevel <= level(AcademicRole.FULL_TIME_TEACHER)
                        && ownerMajor != null
                        && ownerMajor.equals(assignment.getMajorId())
                        && organization.isMajorActive(assignment.getMajorId());
                case FULL_TIME_TEACHER -> false;
            };
        });
    }

    public Long requireOwnerViewEnabled(
            CurrentUser actor, ResourceType type, Long resourceId) {
        Long owner = catalog.owner(type, resourceId);
        if (UserRole.SUPER_ADMIN.name().equals(actor.role())) {
            throw denied();
        }
        if (!owner.equals(actor.teacherId())
                && !Boolean.TRUE.equals(policy(type, resourceId, owner).getViewAllowed())) {
            throw denied();
        }
        return owner;
    }

    private int level(AcademicRole role) {
        return switch (role) {
            case FULL_TIME_TEACHER -> 1;
            case MAJOR_LEADER -> 2;
            case VICE_DEAN -> 3;
            case DEAN, PARTY_SECRETARY -> 4;
        };
    }

    public ResourceAccessPolicy policy(ResourceType type, Long resourceId, Long owner) {
        ResourceAccessPolicy policy = policies.selectOne(new LambdaQueryWrapper<ResourceAccessPolicy>()
                .eq(ResourceAccessPolicy::getResourceType, type.name())
                .eq(ResourceAccessPolicy::getResourceId, resourceId));
        if (policy == null || !owner.equals(policy.getOwnerTeacherId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Resource access policy is unavailable");
        }
        return policy;
    }

    private BusinessException denied() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Resource not found");
    }

    public record AccessDecision(Long ownerTeacherId, ResourceAccessGrant grant) {
    }
}
