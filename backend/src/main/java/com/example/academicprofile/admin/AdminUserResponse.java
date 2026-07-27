package com.example.academicprofile.admin;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String role,
        String status,
        LocalDateTime createdAt) {
}
