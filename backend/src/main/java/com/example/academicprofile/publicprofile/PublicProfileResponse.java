package com.example.academicprofile.publicprofile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PublicProfileResponse(
        Profile profile,
        List<AcademicQualificationItem> academicQualifications,
        List<AreaItem> teachingAreas,
        List<AreaItem> researchAreas,
        List<ProfessionalServiceItem> professionalServices,
        List<WorkingExperienceItem> workingExperiences,
        List<ProjectItem> projects,
        List<TeachingCourseItem> teachingCourses,
        List<PaperItem> papers,
        List<PatentItem> patents,
        List<CertificateItem> certificates) {

    public record Profile(
            Long avatarFileId,
            String displayName,
            String title,
            String department,
            String phone,
            String office,
            String profileEmail,
            String biography,
            String publicSlug) {
    }

    public record AcademicQualificationItem(
            Long id,
            String degree,
            String institution,
            String major,
            LocalDate startDate,
            LocalDate endDate,
            String description) {
    }

    public record AreaItem(
            Long id,
            String name,
            String description) {
    }

    public record ProfessionalServiceItem(
            Long id,
            String title,
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String description) {
    }

    public record WorkingExperienceItem(
            Long id,
            String organization,
            String position,
            LocalDate startDate,
            LocalDate endDate,
            String description) {
    }

    public record ProjectItem(
            Long id,
            String projectName,
            String source,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal fundingAmount,
            String status,
            String description) {
    }

    public record TeachingCourseItem(
            Long id,
            String courseName,
            String semester,
            String className,
            String teachingTarget,
            BigDecimal hours,
            String description) {
    }

    public record PaperItem(
            Long id,
            String title,
            String authors,
            String publicationName,
            String publicationType,
            Integer publishYear,
            String doi,
            String volume,
            String issue,
            String pages,
            String publisher,
            String url,
            String abstractText,
            String keywords) {
    }

    public record PatentItem(
            Long id,
            String patentName,
            String patentNumber,
            String patentType,
            String status,
            LocalDate applicationDate,
            LocalDate authorizationDate,
            String inventors,
            String description) {
    }

    public record CertificateItem(
            Long id,
            String certificateName,
            String certificateType,
            String issuingAuthority,
            LocalDate issueDate,
            String description) {
    }
}
