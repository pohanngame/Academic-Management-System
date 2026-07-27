package com.example.academicprofile.security;

public record CurrentUser(
        Long userId,
        Long teacherId,
        String username,
        String email,
        String role,
        Long authVersion) {

    public CurrentUser(Long userId, Long teacherId, String username, String email, String role) {
        this(userId, teacherId, username, email, role, 0L);
    }
}
