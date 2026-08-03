package com.example.academicprofile.organization;

import com.example.academicprofile.common.exception.BusinessException;

public enum AcademicRole {
    FULL_TIME_TEACHER,
    MAJOR_LEADER,
    VICE_DEAN,
    DEAN,
    PARTY_SECRETARY;

    public static AcademicRole require(String value) {
        try {
            return AcademicRole.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("Invalid academic role");
        }
    }

    public boolean requiresMajor() {
        return this == MAJOR_LEADER;
    }
}
