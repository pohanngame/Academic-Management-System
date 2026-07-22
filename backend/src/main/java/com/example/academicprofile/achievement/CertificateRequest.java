package com.example.academicprofile.achievement;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CertificateRequest(
        @NotBlank @Size(max = 255) String certificateName,
        @Size(max = 128) String certificateType,
        @Size(max = 255) String issuingAuthority,
        LocalDate issueDate,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Boolean isPublic) {
}
