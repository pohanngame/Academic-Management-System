package com.example.academicprofile.access;

import com.example.academicprofile.common.exception.BusinessException;

public enum AccessPermission {
    VIEW,
    DOWNLOAD,
    AI_USE;

    public static AccessPermission require(String value) {
        try {
            return AccessPermission.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BusinessException("Invalid access permission");
        }
    }
}
