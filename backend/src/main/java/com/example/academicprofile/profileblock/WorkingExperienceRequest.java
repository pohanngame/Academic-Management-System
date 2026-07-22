package com.example.academicprofile.profileblock;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkingExperienceRequest(
        @NotBlank @Size(max = 255) String organization,
        @Size(max = 128) String position,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Boolean isPublic) {
}
