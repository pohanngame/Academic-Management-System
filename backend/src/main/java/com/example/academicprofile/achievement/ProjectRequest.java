package com.example.academicprofile.achievement;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank @Size(max = 255) String projectName,
        @Size(max = 255) String source,
        @Size(max = 128) String role,
        LocalDate startDate,
        LocalDate endDate,
        @DecimalMin(value = "0.00") BigDecimal fundingAmount,
        @Size(max = 64) String status,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Boolean isPublic) {
}
