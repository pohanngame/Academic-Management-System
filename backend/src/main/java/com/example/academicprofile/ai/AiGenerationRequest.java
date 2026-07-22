package com.example.academicprofile.ai;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record AiGenerationRequest(
        Long templateFileId,
        @Size(max = 5000) String templateRequirement,
        @NotEmpty List<String> selectedModules) {
}
