package com.example.academicprofile.achievement;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatentRequest(
        @NotBlank @Size(max = 255) String patentName,
        @Size(max = 128) String patentNumber,
        @Size(max = 128) String patentType,
        @Size(max = 64) String status,
        LocalDate applicationDate,
        LocalDate authorizationDate,
        @Size(max = 5000) String inventors,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Boolean isPublic) {
}
