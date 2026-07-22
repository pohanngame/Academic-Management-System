package com.example.academicprofile.teacher;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeacherProfileRequest(
        Long avatarFileId,
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 128) String title,
        @Size(max = 128) String department,
        @Size(max = 64) String phone,
        @Size(max = 128) String office,
        @Email @Size(max = 128) String profileEmail,
        @Size(max = 5000) String biography,
        Boolean publicEnabled,
        @Size(max = 128) String publicSlug,
        String fieldVisibilityConfig) {
}
