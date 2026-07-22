package com.example.academicprofile.bibtex;

import java.time.LocalDateTime;
import java.util.List;

public record BibtexImportTaskResponse(
        Long id,
        String sourceType,
        String fileName,
        String status,
        Integer totalCount,
        Integer successCount,
        Integer failedCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<BibtexImportItemResponse> items) {

    public static BibtexImportTaskResponse from(BibtexImportTask task) {
        return from(task, null);
    }

    public static BibtexImportTaskResponse from(BibtexImportTask task, List<BibtexImportItemResponse> items) {
        return new BibtexImportTaskResponse(
                task.getId(),
                task.getSourceType(),
                task.getFileName(),
                task.getStatus(),
                task.getTotalCount(),
                task.getSuccessCount(),
                task.getFailedCount(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                items);
    }
}
