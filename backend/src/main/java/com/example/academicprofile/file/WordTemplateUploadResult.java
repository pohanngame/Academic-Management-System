package com.example.academicprofile.file;

import com.example.academicprofile.word.WordTemplateValidationResult;

public record WordTemplateUploadResult(
        FileMetadata metadata,
        WordTemplateValidationResult validation) {
}
