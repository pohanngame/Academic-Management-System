package com.example.academicprofile.ai;

import java.time.LocalDateTime;

public record AiGenerationResultResponse(
        Long id,
        Long taskId,
        String status,
        String draftContent,
        String editedContent,
        String confirmedContent,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AiGenerationResultResponse from(AiGenerationResult result) {
        if (result == null) {
            return null;
        }
        return new AiGenerationResultResponse(
                result.getId(),
                result.getTaskId(),
                result.getStatus(),
                result.getDraftContent(),
                result.getEditedContent(),
                result.getConfirmedContent(),
                result.getConfirmedAt(),
                result.getCreatedAt(),
                result.getUpdatedAt());
    }
}
