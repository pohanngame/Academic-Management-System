package com.example.academicprofile.access;

import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileBusinessType;

public enum ResourceType {
    ACADEMIC_QUALIFICATION,
    TEACHING_AREA,
    RESEARCH_AREA,
    PROFESSIONAL_SERVICE,
    WORKING_EXPERIENCE,
    PROJECT,
    TEACHING_COURSE,
    PAPER,
    PATENT,
    CERTIFICATE,
    FILE_ATTACHMENT;

    public static ResourceType require(String value) {
        try {
            return ResourceType.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("Invalid resource type");
        }
    }

    public static ResourceType fromFileBusinessType(String value) {
        FileBusinessType type;
        try {
            type = FileBusinessType.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("Unsupported shared attachment type");
        }
        return switch (type) {
            case PAPER -> PAPER;
            case PATENT -> PATENT;
            case CERTIFICATE -> CERTIFICATE;
            case AVATAR, AI_WORD_TEMPLATE, CERTIFICATE_OCR_STAGING ->
                    throw new BusinessException("File is not a shared business attachment");
        };
    }
}
