package com.example.academicprofile.ocr;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record OcrResultRequest(
        @Size(max = 500) String title,
        @Size(max = 5000) String authors,
        @Size(max = 255) String publicationName,
        @Size(max = 64) String publicationType,
        @Min(0) @Max(9999) Integer publishYear,
        @Size(max = 255) String doi,
        @Size(max = 64) String volume,
        @Size(max = 64) String issue,
        @Size(max = 64) String pages,
        @Size(max = 255) String publisher,
        @Size(max = 1024) String url,
        @Size(max = 20000) String abstractText,
        @Size(max = 1000) String keywords,
        @Size(max = 255) String patentName,
        @Size(max = 128) String patentNumber,
        @Size(max = 128) String patentType,
        @Size(max = 64) String patentStatus,
        LocalDate applicationDate,
        LocalDate authorizationDate,
        @Size(max = 5000) String inventors,
        @Size(max = 255) String certificateName,
        @Size(max = 128) String certificateType,
        @Size(max = 255) String issuingAuthority,
        LocalDate issueDate,
        @Size(max = 5000) String description) {
}
