package com.example.academicprofile.word;

import com.example.academicprofile.common.exception.BusinessException;

public enum WordOutputTemplateType {
    SYSTEM_GENERAL,
    TEACHER_ACHIEVEMENT,
    SIMPLE_REPORT,
    CUSTOM;

    public static WordOutputTemplateType resolve(String value, Long templateFileId) {
        if (value == null || value.isBlank()) {
            return templateFileId == null ? SYSTEM_GENERAL : CUSTOM;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("不支持的 Word 输出样式");
        }
    }
}
