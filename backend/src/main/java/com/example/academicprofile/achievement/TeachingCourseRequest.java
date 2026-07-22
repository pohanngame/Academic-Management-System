package com.example.academicprofile.achievement;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeachingCourseRequest(
        @NotBlank @Size(max = 255) String courseName,
        @Size(max = 128) String semester,
        @Size(max = 255) String className,
        @Size(max = 255) String teachingTarget,
        @DecimalMin(value = "0.00") BigDecimal hours,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Boolean isPublic) {
}
