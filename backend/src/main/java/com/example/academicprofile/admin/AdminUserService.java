package com.example.academicprofile.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.teacher.TeacherProfileService;
import com.example.academicprofile.user.UserAccount;
import com.example.academicprofile.user.UserAccountService;
import com.example.academicprofile.user.UserRole;
import com.example.academicprofile.user.UserStatus;

@Service
public class AdminUserService {

    private final UserAccountService users;
    private final TeacherProfileService teachers;
    private final AdminAuditService audit;
    private final TransactionTemplate transactions;

    public AdminUserService(
            UserAccountService users,
            TeacherProfileService teachers,
            AdminAuditService audit,
            TransactionTemplate transactions) {
        this.users = users;
        this.teachers = teachers;
        this.audit = audit;
        this.transactions = transactions;
    }

    public AdminPageResponse<AdminUserResponse> list(
            int page, int size, String search, String role, String status) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("Invalid pagination");
        }
        String normalizedRole = normalizeOptionalRole(role);
        String normalizedStatus = normalizeOptionalStatus(status);
        Page<UserAccount> result = users.page(page, size, search, normalizedRole, normalizedStatus);
        List<AdminUserResponse> items = result.getRecords().stream().map(this::toResponse).toList();
        return new AdminPageResponse<>(items, result.getCurrent(), result.getSize(),
                result.getTotal(), result.getPages());
    }

    public AdminUserResponse changeRole(CurrentUser operator, Long targetId, AdminRoleRequest request) {
        try {
            UserRole role = UserRole.require(request.role());
            return transactions.execute(status -> {
                AdminUserResponse response = changeRoleInTransaction(operator, targetId, role);
                audit.record(operator.userId(), "USER_ROLE_CHANGE", targetId, true,
                        "Role changed to " + role.name());
                return response;
            });
        } catch (RuntimeException ex) {
            recordRejected(operator.userId(), "USER_ROLE_CHANGE", targetId);
            throw ex;
        }
    }

    public AdminUserResponse changeStatus(CurrentUser operator, Long targetId, AdminStatusRequest request) {
        try {
            UserStatus requestedStatus = UserStatus.require(request.status());
            return transactions.execute(status -> {
                AdminUserResponse response =
                        changeStatusInTransaction(operator, targetId, requestedStatus);
                audit.record(operator.userId(), "USER_STATUS_CHANGE", targetId, true,
                        "Status changed to " + requestedStatus.name());
                return response;
            });
        } catch (RuntimeException ex) {
            recordRejected(operator.userId(), "USER_STATUS_CHANGE", targetId);
            throw ex;
        }
    }

    private AdminUserResponse changeRoleInTransaction(CurrentUser operator, Long targetId, UserRole requestedRole) {
        UserAccount target = lockTarget(targetId);
        UserRole currentRole = UserRole.require(target.getRole());
        if (target.getId().equals(operator.userId()) && requestedRole != UserRole.SUPER_ADMIN) {
            throw new BusinessException(HttpStatus.CONFLICT, "You cannot revoke your own SUPER_ADMIN role");
        }
        if (currentRole == UserRole.SUPER_ADMIN && requestedRole == UserRole.TEACHER
                && UserStatus.require(target.getStatus()) == UserStatus.ACTIVE) {
            ensureAnotherActiveAdmin(target.getId());
        }
        if (currentRole != requestedRole) {
            target.setRole(requestedRole.name());
            invalidateTokens(target);
            users.update(target);
        }
        return toResponse(target);
    }

    private AdminUserResponse changeStatusInTransaction(
            CurrentUser operator, Long targetId, UserStatus requestedStatus) {
        UserAccount target = lockTarget(targetId);
        UserStatus currentStatus = UserStatus.require(target.getStatus());
        if (target.getId().equals(operator.userId()) && requestedStatus == UserStatus.DISABLED) {
            throw new BusinessException(HttpStatus.CONFLICT, "You cannot disable your own account");
        }
        if (UserRole.require(target.getRole()) == UserRole.SUPER_ADMIN
                && currentStatus == UserStatus.ACTIVE
                && requestedStatus == UserStatus.DISABLED) {
            ensureAnotherActiveAdmin(target.getId());
        }
        if (currentStatus != requestedStatus) {
            target.setStatus(requestedStatus.name());
            invalidateTokens(target);
            users.update(target);
        }
        return toResponse(target);
    }

    private UserAccount lockTarget(Long targetId) {
        if (targetId == null || targetId < 1) {
            throw new BusinessException("Invalid target user");
        }
        return users.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void ensureAnotherActiveAdmin(Long targetId) {
        boolean anotherExists = users.lockActiveSuperAdmins().stream()
                .anyMatch(admin -> !admin.getId().equals(targetId));
        if (!anotherExists) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "The last active SUPER_ADMIN cannot be disabled or downgraded");
        }
    }

    private void invalidateTokens(UserAccount target) {
        target.setAuthVersion(target.getAuthVersion() == null ? 1L : target.getAuthVersion() + 1L);
    }

    private AdminUserResponse toResponse(UserAccount user) {
        String displayName = teachers.findByUserId(user.getId())
                .map(profile -> profile.getDisplayName())
                .orElse("");
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), displayName,
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }

    private String normalizeOptionalRole(String role) {
        return StringUtils.hasText(role) ? UserRole.require(role).name() : null;
    }

    private String normalizeOptionalStatus(String status) {
        return StringUtils.hasText(status) ? UserStatus.require(status).name() : null;
    }

    private void recordRejected(Long operatorId, String action, Long targetId) {
        try {
            audit.record(operatorId, action, targetId, false, "Administrative change rejected");
        } catch (RuntimeException ignored) {
            // The original controlled failure remains authoritative.
        }
    }
}
