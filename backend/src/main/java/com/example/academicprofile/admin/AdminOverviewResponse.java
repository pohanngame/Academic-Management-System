package com.example.academicprofile.admin;

import java.time.LocalDateTime;

public record AdminOverviewResponse(
        long totalUsers,
        long teacherUsers,
        long superAdminUsers,
        long activeUsers,
        long disabledUsers,
        long uploadFiles,
        long uploadBytes,
        BackupResult latestBackup) {

    public record BackupResult(boolean success, String summary, LocalDateTime createdAt) {
    }
}
