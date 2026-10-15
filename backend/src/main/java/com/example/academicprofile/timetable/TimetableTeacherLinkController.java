package com.example.academicprofile.timetable;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;
import com.example.academicprofile.timetable.TimetableDtos.TeacherLinkRequest;
import com.example.academicprofile.timetable.TimetableDtos.TeacherLinkResponse;

import jakarta.validation.Valid;

/** 仅 SUPER_ADMIN：/api/admin/** 由 SecurityConfig 统一限制。 */
@RestController
@RequestMapping("/api/admin/timetable/teacher-links")
public class TimetableTeacherLinkController {
    private final TimetableTeacherLinkService service;

    public TimetableTeacherLinkController(TimetableTeacherLinkService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TeacherLinkResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<TeacherLinkResponse> upsert(@Valid @RequestBody TeacherLinkRequest request) {
        return ApiResponse.ok(service.upsert(SecurityUtils.currentUser().userId(), request));
    }

    @DeleteMapping
    public ApiResponse<Void> delete(@RequestParam String sourceName) {
        service.delete(SecurityUtils.currentUser().userId(), sourceName);
        return ApiResponse.ok(null);
    }
}
