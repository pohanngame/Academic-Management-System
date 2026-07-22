package com.example.academicprofile.ocr;

import jakarta.validation.constraints.NotNull;

public record OcrTaskRequest(
        @NotNull Long fileId,
        @NotNull OcrTargetType targetType) {
}
