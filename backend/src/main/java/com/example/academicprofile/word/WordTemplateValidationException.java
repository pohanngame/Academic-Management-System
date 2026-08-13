package com.example.academicprofile.word;

import org.springframework.http.HttpStatus;

import com.example.academicprofile.common.exception.BusinessException;

public class WordTemplateValidationException extends BusinessException {

    private final WordTemplateValidationCode code;
    private final int validPlaceholderCount;

    public WordTemplateValidationException(
            WordTemplateValidationCode code, String message, int validPlaceholderCount) {
        this(HttpStatus.BAD_REQUEST, code, message, validPlaceholderCount);
    }

    public WordTemplateValidationException(
            HttpStatus status,
            WordTemplateValidationCode code,
            String message,
            int validPlaceholderCount) {
        super(status, message);
        this.code = code;
        this.validPlaceholderCount = validPlaceholderCount;
    }

    public WordTemplateValidationCode code() {
        return code;
    }

    public int validPlaceholderCount() {
        return validPlaceholderCount;
    }
}
