package com.example.academicprofile.exporting;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExportTemplateRequest(
        @NotBlank @Size(max = 128) String templateName,
        @NotNull ExportType exportType,
        @NotEmpty List<@Valid ExportModuleSelection> modules) {
}
