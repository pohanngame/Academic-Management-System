package com.example.academicprofile.ai;

import com.example.academicprofile.word.WordTemplateValidationResult;

public record AiWordTemplateValidationResponse(
        String code,
        String message,
        int validPlaceholderCount,
        String insertionLocation,
        boolean autoAppended) {

    public static AiWordTemplateValidationResponse from(WordTemplateValidationResult result) {
        return new AiWordTemplateValidationResponse(
                result.code().name(),
                result.message(),
                result.validPlaceholderCount(),
                result.insertionLocation(),
                result.autoAppended());
    }
}
