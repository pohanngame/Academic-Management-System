package com.example.academicprofile.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(min = 8, max = 64) String confirmPassword,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 128) String department,
        @Size(max = 128) String title) {
}
