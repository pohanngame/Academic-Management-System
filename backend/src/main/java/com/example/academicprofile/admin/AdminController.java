package com.example.academicprofile.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminOverviewService overviewService;
    private final AdminUserService userService;
    private final AdminAuditService auditService;

    public AdminController(
            AdminOverviewService overviewService,
            AdminUserService userService,
            AdminAuditService auditService) {
        this.overviewService = overviewService;
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(overviewService.get());
    }

    @GetMapping("/users")
    public ApiResponse<AdminPageResponse<AdminUserResponse>> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(userService.list(page, size, search, role, status));
    }

    @PatchMapping("/users/{id}/role")
    public ApiResponse<AdminUserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody AdminRoleRequest request) {
        return ApiResponse.ok(userService.changeRole(SecurityUtils.currentUser(), id, request));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminStatusRequest request) {
        return ApiResponse.ok(userService.changeStatus(SecurityUtils.currentUser(), id, request));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<AdminPageResponse<AdminAuditResponse>> auditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new com.example.academicprofile.common.exception.BusinessException("Invalid pagination");
        }
        Page<AdminAuditLog> result = auditService.page(page, size);
        List<AdminAuditResponse> items = result.getRecords().stream()
                .map(log -> new AdminAuditResponse(
                        log.getId(), log.getOperatorUserId(), log.getActionType(), log.getTargetUserId(),
                        Boolean.TRUE.equals(log.getSuccess()), log.getSummary(), log.getCreatedAt()))
                .toList();
        return ApiResponse.ok(new AdminPageResponse<>(
                items, result.getCurrent(), result.getSize(), result.getTotal(), result.getPages()));
    }
}
