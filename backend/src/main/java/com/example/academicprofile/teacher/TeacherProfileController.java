package com.example.academicprofile.teacher;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/teacher/profile")
public class TeacherProfileController {

    private final TeacherProfileService teacherProfileService;

    public TeacherProfileController(TeacherProfileService teacherProfileService) {
        this.teacherProfileService = teacherProfileService;
    }

    @GetMapping
    public ApiResponse<TeacherProfileResponse> getCurrentProfile() {
        Long teacherId = SecurityUtils.currentUser().teacherId();
        return ApiResponse.ok(TeacherProfileResponse.from(teacherProfileService.getByTeacherId(teacherId)));
    }

    @PutMapping
    public ApiResponse<TeacherProfileResponse> updateCurrentProfile(
            @Valid @RequestBody TeacherProfileRequest request) {
        Long teacherId = SecurityUtils.currentUser().teacherId();
        return ApiResponse.ok(TeacherProfileResponse.from(teacherProfileService.updateCurrent(teacherId, request)));
    }
}
