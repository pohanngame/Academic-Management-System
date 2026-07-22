package com.example.academicprofile.file;

import java.time.LocalDateTime;

public record FileMetadataResponse(
        Long id,
        String originalName,
        String fileExt,
        String mimeType,
        Long fileSize,
        String businessType,
        Long businessId,
        LocalDateTime createdAt) {

    public static FileMetadataResponse from(FileMetadata metadata) {
        return new FileMetadataResponse(
                metadata.getId(),
                metadata.getOriginalName(),
                metadata.getFileExt(),
                metadata.getMimeType(),
                metadata.getFileSize(),
                metadata.getBusinessType(),
                metadata.getBusinessId(),
                metadata.getCreatedAt());
    }
}
