package com.example.academicprofile.ai;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record AiGenerationRequest(
        Long templateFileId,
        @Size(max = 5000) String templateRequirement,
        @NotEmpty List<String> selectedModules,
        @Size(max = 50) List<Long> sourceTeacherIds,
        Integer startYear,
        Integer endYear,
        @Size(max = 2) List<String> competitionCategories,
        Map<String, List<String>> selectedFields,
        String outputMode,
        @Size(max = 20) List<Long> selectedAttachmentIds,
        String templateType,
        TimetableQuery timetableQuery) {

    /**
     * 课表占用查询参数。只有选中 timetableOccupancy 时才应传；
     * 未选中时载荷不得含课表。
     */
    public record TimetableQuery(
            String semesterCode, Integer week, Long majorId, List<Long> teacherIds) {
    }

    public AiGenerationRequest(
            Long templateFileId, String templateRequirement, List<String> selectedModules) {
        this(templateFileId, templateRequirement, selectedModules, null, null, null, null, null, "AI_DRAFT", null, null, null);
    }

    public AiGenerationRequest(
            Long templateFileId,
            String templateRequirement,
            List<String> selectedModules,
            List<Long> sourceTeacherIds,
            Integer startYear,
            Integer endYear,
            List<String> competitionCategories,
            Map<String, List<String>> selectedFields,
            String outputMode,
            List<Long> selectedAttachmentIds) {
        this(templateFileId, templateRequirement, selectedModules, sourceTeacherIds, startYear, endYear,
                competitionCategories, selectedFields, outputMode, selectedAttachmentIds, null, null);
    }
}
