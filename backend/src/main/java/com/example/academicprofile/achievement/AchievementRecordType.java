package com.example.academicprofile.achievement;

import com.example.academicprofile.access.ResourceType;
import com.example.academicprofile.common.exception.BusinessException;
import com.example.academicprofile.file.FileBusinessType;

public enum AchievementRecordType {
    PROJECTS("project", ResourceType.PROJECT, null),
    TEACHING_COURSES("teaching_course", ResourceType.TEACHING_COURSE, null),
    PAPERS("paper", ResourceType.PAPER, FileBusinessType.PAPER),
    PATENTS("patent", ResourceType.PATENT, FileBusinessType.PATENT),
    CERTIFICATES("certificate", ResourceType.CERTIFICATE, FileBusinessType.CERTIFICATE);

    private final String table;
    private final ResourceType resourceType;
    private final FileBusinessType fileBusinessType;

    AchievementRecordType(String table, ResourceType resourceType, FileBusinessType fileBusinessType) {
        this.table = table;
        this.resourceType = resourceType;
        this.fileBusinessType = fileBusinessType;
    }

    public String table() { return table; }
    public ResourceType resourceType() { return resourceType; }
    public FileBusinessType fileBusinessType() { return fileBusinessType; }

    public static AchievementRecordType fromPath(String value) {
        if (value == null) throw new BusinessException("Unsupported achievement type");
        return switch (value) {
            case "projects" -> PROJECTS;
            case "teachingCourses", "teaching-courses" -> TEACHING_COURSES;
            case "papers" -> PAPERS;
            case "patents" -> PATENTS;
            case "certificates" -> CERTIFICATES;
            default -> throw new BusinessException("Unsupported achievement type");
        };
    }
}
