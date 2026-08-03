package com.example.academicprofile.achievement;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaperRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 5000) String authors,
        @Size(max = 255) String publicationName,
        @Size(max = 64) String publicationType,
        @Size(max = 16) String venueType,
        @Size(max = 128) String venueAbbreviation,
        @Size(max = 16) String issn,
        @Size(max = 16) String eissn,
        Long ccfReferenceId,
        Boolean ccfConfirmed,
        @Size(max = 1) String manualCcfRank,
        Integer manualCcfCategoryId,
        @Size(max = 255) String manualCcfCategoryName,
        Long journalMetricReferenceId,
        @Min(1900) @Max(2200) Integer jcrDataYear,
        BigDecimal journalImpactFactor,
        @Size(max = 2) String jcrQuartile,
        @Size(max = 255) String jcrSubject,
        @Size(max = 500) String metricSourceDescription,
        @Size(max = 1024) String metricSourceUrl,
        Boolean metricConfirmed,
        @Min(0) @Max(9999) Integer publishYear,
        @Size(max = 255) String doi,
        @Size(max = 64) String volume,
        @Size(max = 64) String issue,
        @Size(max = 64) String pages,
        @Size(max = 255) String publisher,
        @Size(max = 1024) String url,
        @Size(max = 20000) String abstractText,
        @Size(max = 1000) String keywords,
        Integer sortOrder,
        Boolean isPublic) {
}
