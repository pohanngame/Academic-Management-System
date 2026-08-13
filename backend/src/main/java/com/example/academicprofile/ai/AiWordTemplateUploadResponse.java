package com.example.academicprofile.ai;

import com.example.academicprofile.file.FileMetadataResponse;

public record AiWordTemplateUploadResponse(
        FileMetadataResponse file,
        AiWordTemplateValidationResponse validation) {
}
