package com.example.academicprofile.word;

public record WordTemplateValidationResult(
        WordTemplateValidationCode code,
        String message,
        int validPlaceholderCount,
        String insertionLocation,
        boolean autoAppended) {

    public boolean valid() {
        return code == WordTemplateValidationCode.VALID_EXACT_PLACEHOLDER;
    }
}
