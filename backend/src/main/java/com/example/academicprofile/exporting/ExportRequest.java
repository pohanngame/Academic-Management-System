package com.example.academicprofile.exporting;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ExportRequest(
        @NotNull ExportType exportType,
        @NotEmpty List<@Valid ExportModuleSelection> modules) {
}
