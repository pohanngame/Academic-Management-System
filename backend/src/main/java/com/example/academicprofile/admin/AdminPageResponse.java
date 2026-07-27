package com.example.academicprofile.admin;

import java.util.List;

public record AdminPageResponse<T>(
        List<T> items,
        long page,
        long size,
        long total,
        long pages) {
}
