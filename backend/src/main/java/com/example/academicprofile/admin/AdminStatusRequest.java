package com.example.academicprofile.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminStatusRequest(@NotBlank String status) {
}
