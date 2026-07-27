package com.example.academicprofile.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminRoleRequest(@NotBlank String role) {
}
