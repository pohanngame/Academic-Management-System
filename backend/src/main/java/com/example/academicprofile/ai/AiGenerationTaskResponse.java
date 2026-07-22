package com.example.academicprofile.ai;

import java.time.LocalDateTime;
import java.util.List;

import com.example.academicprofile.file.FileMetadata;

public record AiGenerationTaskResponse(
        Long id,
        Long templateFileId,
        String templateFileName,
        Boolean templateDeleted,
        String templateRequirement,
        List<String> selectedModules,
        String provider,
        String modelName,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        AiGenerationResultResponse result) {

    public static AiGenerationTaskResponse from(
            AiGenerationTask task,
            List<String> selectedModules,
            AiGenerationResult result,
            FileMetadata templateFile) {
        return new AiGenerationTaskResponse(
                task.getId(),
                task.getTemplateFileId(),
                templateFile == null ? null : templateFile.getOriginalName(),
                templateFile == null ? null : templateFile.getDeleted(),
                task.getTemplateRequirement(),
                selectedModules,
                task.getProvider(),
                task.getModelName(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                AiGenerationResultResponse.from(result));
    }
}
