package com.example.academicprofile.profileblock;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcademicQualificationRequest(
        @NotBlank @Size(max = 128) String degree,
        @Size(max = 255) String institution,
        @Size(max = 255) String major,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Boolean isPublic) {
}
