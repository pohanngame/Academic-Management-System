package com.example.academicprofile.backup;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RestoreStageMetadata(
        UUID id,
        long size,
        String sha256,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        String status) {
}
