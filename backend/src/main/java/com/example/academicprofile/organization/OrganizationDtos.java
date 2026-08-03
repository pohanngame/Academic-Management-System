package com.example.academicprofile.organization;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class OrganizationDtos {
    private OrganizationDtos() {
    }

    public record MajorRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            Boolean active) {
    }

    public record TeacherOrganizationRequest(
            Long majorId,
            @NotEmpty @Size(max = 5) List<@Valid RoleAssignmentRequest> roles) {
    }

    public record RoleAssignmentRequest(
            @NotBlank String role,
            Long majorId) {
    }

    public record RoleAssignmentResponse(String role, Long majorId) {
    }

    public record TeacherOrganizationResponse(
            Long teacherId,
            Long majorId,
            List<RoleAssignmentResponse> roles) {
    }

    public record TeacherOption(Long teacherId, String displayName) {
    }
}
