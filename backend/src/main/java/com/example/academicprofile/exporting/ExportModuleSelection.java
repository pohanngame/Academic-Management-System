package com.example.academicprofile.exporting;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record ExportModuleSelection(
        @NotBlank String moduleKey,
        List<String> fields) {
}
