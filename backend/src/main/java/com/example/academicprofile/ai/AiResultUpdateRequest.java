package com.example.academicprofile.ai;

import jakarta.validation.constraints.NotBlank;

public record AiResultUpdateRequest(@NotBlank String editedContent) {
}
