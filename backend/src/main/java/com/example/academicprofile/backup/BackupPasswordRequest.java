package com.example.academicprofile.backup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BackupPasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword) {

    @Override
    public String toString() {
        return "BackupPasswordRequest[currentPassword=[REDACTED]]";
    }
}
