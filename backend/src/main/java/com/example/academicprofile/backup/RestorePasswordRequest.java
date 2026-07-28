package com.example.academicprofile.backup;

import jakarta.validation.constraints.NotBlank;

public record RestorePasswordRequest(
        @NotBlank(message = "Current password is required") String currentPassword) {

    @Override
    public String toString() {
        return "RestorePasswordRequest[currentPassword=[redacted]]";
    }
}
