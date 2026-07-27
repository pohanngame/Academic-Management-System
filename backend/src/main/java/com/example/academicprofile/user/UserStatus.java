package com.example.academicprofile.user;

import java.util.Arrays;

import com.example.academicprofile.common.exception.BusinessException;

public enum UserStatus {
    ACTIVE,
    DISABLED;

    public static UserStatus require(String value) {
        return Arrays.stream(values())
                .filter(status -> status.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unknown user status"));
    }
}
