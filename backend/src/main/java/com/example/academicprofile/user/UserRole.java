package com.example.academicprofile.user;

import java.util.Arrays;

import com.example.academicprofile.common.exception.BusinessException;

public enum UserRole {
    TEACHER,
    SUPER_ADMIN;

    public static UserRole require(String value) {
        return Arrays.stream(values())
                .filter(role -> role.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unknown user role"));
    }
}
