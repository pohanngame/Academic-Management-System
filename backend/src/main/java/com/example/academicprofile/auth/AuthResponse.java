package com.example.academicprofile.auth;

public record AuthResponse(String token, AuthUserResponse user) {
}
