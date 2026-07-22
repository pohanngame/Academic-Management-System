package com.example.academicprofile.exporting;

import java.util.List;

public record ExportModuleDefinition(String key, String label, boolean multiple, List<ExportFieldDefinition> fields) {
}
