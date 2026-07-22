package com.example.academicprofile.teacher;

public record TeacherProfileResponse(
        Long id,
        Long userId,
        Long avatarFileId,
        String displayName,
        String title,
        String department,
        String phone,
        String office,
        String profileEmail,
        String biography,
        Boolean publicEnabled,
        String publicSlug,
        String fieldVisibilityConfig) {

    public static TeacherProfileResponse from(TeacherProfile profile) {
        return new TeacherProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getAvatarFileId(),
                profile.getDisplayName(),
                profile.getTitle(),
                profile.getDepartment(),
                profile.getPhone(),
                profile.getOffice(),
                profile.getProfileEmail(),
                profile.getBiography(),
                profile.getPublicEnabled(),
                profile.getPublicSlug(),
                profile.getFieldVisibilityConfig());
    }
}
