package com.example.academicprofile.admin;

import java.time.LocalDateTime;

public record AdminAuditResponse(
        Long id,
        Long operatorUserId,
        String actionType,
        Long targetUserId,
        boolean success,
        String summary,
        LocalDateTime createdAt) {
}
