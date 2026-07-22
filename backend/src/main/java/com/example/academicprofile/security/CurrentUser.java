package com.example.academicprofile.security;

public record CurrentUser(Long userId, Long teacherId, String username, String email, String role) {
}
