package com.example.academicprofile.auth;

public record AuthUserResponse(
        Long userId,
        Long teacherId,
        String username,
        String email,
        String role,
        String displayName) {
}
