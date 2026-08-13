package com.example.academicprofile.ai;

public record AiSourceTeacherResponse(
        Long teacherId,
        String displayName,
        String majorName,
        boolean self,
        long availableResourceCount,
        long availableAttachmentCount) {
}
