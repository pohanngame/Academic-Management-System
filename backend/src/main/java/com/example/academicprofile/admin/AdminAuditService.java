package com.example.academicprofile.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class AdminAuditService {

    private final AdminAuditLogMapper mapper;

    public AdminAuditService(AdminAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void record(Long operatorUserId, String action, Long targetUserId, boolean success, String summary) {
        AdminAuditLog log = new AdminAuditLog();
        log.setOperatorUserId(operatorUserId);
        log.setActionType(action);
        log.setTargetUserId(targetUserId);
        log.setSuccess(success);
        log.setSummary(sanitize(summary));
        mapper.insert(log);
    }

    public Page<AdminAuditLog> page(long page, long size) {
        return mapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<AdminAuditLog>().orderByDesc(AdminAuditLog::getCreatedAt));
    }

    public AdminAuditLog latestBackupResult() {
        return mapper.selectOne(new LambdaQueryWrapper<AdminAuditLog>()
                .in(AdminAuditLog::getActionType, "BACKUP_CREATE", "BACKUP_FAILED")
                .orderByDesc(AdminAuditLog::getCreatedAt)
                .last("LIMIT 1"));
    }

    public AdminAuditLog latestSuccessful(String action) {
        return mapper.selectOne(new LambdaQueryWrapper<AdminAuditLog>()
                .eq(AdminAuditLog::getActionType, action)
                .eq(AdminAuditLog::getSuccess, true)
                .orderByDesc(AdminAuditLog::getCreatedAt)
                .last("LIMIT 1"));
    }

    private String sanitize(String summary) {
        if (summary == null) {
            return null;
        }
        String sanitized = summary.replaceAll("(?i)(authorization|bearer|password|api[_ -]?key|token)", "[redacted]");
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }
}
