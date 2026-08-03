package com.example.academicprofile.organization;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.organization.OrganizationDtos.MajorRequest;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOrganizationRequest;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOrganizationResponse;
import com.example.academicprofile.organization.OrganizationDtos.TeacherOption;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/organization")
public class OrganizationController {
    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping("/majors")
    public ApiResponse<List<Major>> majors(
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return ApiResponse.ok(service.listMajors(includeInactive));
    }

    @PostMapping("/majors")
    public ApiResponse<Major> createMajor(@Valid @RequestBody MajorRequest request) {
        return ApiResponse.ok(service.createMajor(SecurityUtils.currentUser(), request));
    }

    @PutMapping("/majors/{id}")
    public ApiResponse<Major> updateMajor(
            @PathVariable Long id, @Valid @RequestBody MajorRequest request) {
        return ApiResponse.ok(service.updateMajor(SecurityUtils.currentUser(), id, request));
    }

    @GetMapping("/teachers/{teacherId}")
    public ApiResponse<TeacherOrganizationResponse> teacher(@PathVariable Long teacherId) {
        return ApiResponse.ok(service.getTeacherOrganization(teacherId));
    }

    @GetMapping("/teachers")
    public ApiResponse<List<TeacherOption>> teachers() {
        return ApiResponse.ok(service.listTeachers());
    }

    @PutMapping("/teachers/{teacherId}")
    public ApiResponse<TeacherOrganizationResponse> updateTeacher(
            @PathVariable Long teacherId,
            @Valid @RequestBody TeacherOrganizationRequest request) {
        return ApiResponse.ok(service.updateTeacherOrganization(
                SecurityUtils.currentUser(), teacherId, request));
    }
}
