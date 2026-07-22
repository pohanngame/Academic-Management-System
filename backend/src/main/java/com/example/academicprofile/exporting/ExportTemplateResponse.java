package com.example.academicprofile.exporting;

import java.time.LocalDateTime;
import java.util.List;

public record ExportTemplateResponse(
        Long id,
        String templateName,
        ExportType exportType,
        List<ExportModuleSelection> modules,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
