package com.example.academicprofile.ocr;

import java.time.LocalDateTime;
import java.util.List;

public record OcrTaskResponse(
        Long id,
        Long fileId,
        String targetType,
        String recognitionMode,
        String status,
        String errorMessage,
        String rawText,
        Integer resultCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OcrResultResponse> results) {

    public static OcrTaskResponse from(OcrTask task) {
        return from(task, null);
    }

    public static OcrTaskResponse from(OcrTask task, List<OcrResultResponse> results) {
        return new OcrTaskResponse(
                task.getId(),
                task.getFileId(),
                task.getTargetType(),
                task.getRecognitionMode(),
                task.getStatus(),
                task.getErrorMessage(),
                task.getRawText(),
                task.getResultCount(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                results);
    }
}
