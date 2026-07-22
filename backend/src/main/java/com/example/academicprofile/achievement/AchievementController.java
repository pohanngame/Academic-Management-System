package com.example.academicprofile.achievement;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/teacher")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @GetMapping("/projects")
    public ApiResponse<List<Project>> listProjects() {
        return ApiResponse.ok(achievementService.listProjects(currentTeacherId()));
    }

    @PostMapping("/projects")
    public ApiResponse<Project> createProject(@Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(achievementService.createProject(currentTeacherId(), request));
    }

    @PutMapping("/projects/{id}")
    public ApiResponse<Project> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(achievementService.updateProject(currentTeacherId(), id, request));
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable Long id) {
        achievementService.deleteProject(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/teaching-courses")
    public ApiResponse<List<TeachingCourse>> listTeachingCourses() {
        return ApiResponse.ok(achievementService.listTeachingCourses(currentTeacherId()));
    }

    @PostMapping("/teaching-courses")
    public ApiResponse<TeachingCourse> createTeachingCourse(@Valid @RequestBody TeachingCourseRequest request) {
        return ApiResponse.ok(achievementService.createTeachingCourse(currentTeacherId(), request));
    }

    @PutMapping("/teaching-courses/{id}")
    public ApiResponse<TeachingCourse> updateTeachingCourse(
            @PathVariable Long id,
            @Valid @RequestBody TeachingCourseRequest request) {
        return ApiResponse.ok(achievementService.updateTeachingCourse(currentTeacherId(), id, request));
    }

    @DeleteMapping("/teaching-courses/{id}")
    public ApiResponse<Void> deleteTeachingCourse(@PathVariable Long id) {
        achievementService.deleteTeachingCourse(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/papers")
    public ApiResponse<List<Paper>> listPapers() {
        return ApiResponse.ok(achievementService.listPapers(currentTeacherId()));
    }

    @PostMapping("/papers")
    public ApiResponse<Paper> createPaper(@Valid @RequestBody PaperRequest request) {
        return ApiResponse.ok(achievementService.createPaper(currentTeacherId(), request));
    }

    @PutMapping("/papers/{id}")
    public ApiResponse<Paper> updatePaper(@PathVariable Long id, @Valid @RequestBody PaperRequest request) {
        return ApiResponse.ok(achievementService.updatePaper(currentTeacherId(), id, request));
    }

    @DeleteMapping("/papers/{id}")
    public ApiResponse<Void> deletePaper(@PathVariable Long id) {
        achievementService.deletePaper(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/patents")
    public ApiResponse<List<Patent>> listPatents() {
        return ApiResponse.ok(achievementService.listPatents(currentTeacherId()));
    }

    @PostMapping("/patents")
    public ApiResponse<Patent> createPatent(@Valid @RequestBody PatentRequest request) {
        return ApiResponse.ok(achievementService.createPatent(currentTeacherId(), request));
    }

    @PutMapping("/patents/{id}")
    public ApiResponse<Patent> updatePatent(@PathVariable Long id, @Valid @RequestBody PatentRequest request) {
        return ApiResponse.ok(achievementService.updatePatent(currentTeacherId(), id, request));
    }

    @DeleteMapping("/patents/{id}")
    public ApiResponse<Void> deletePatent(@PathVariable Long id) {
        achievementService.deletePatent(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/certificates")
    public ApiResponse<List<Certificate>> listCertificates() {
        return ApiResponse.ok(achievementService.listCertificates(currentTeacherId()));
    }

    @PostMapping("/certificates")
    public ApiResponse<Certificate> createCertificate(@Valid @RequestBody CertificateRequest request) {
        return ApiResponse.ok(achievementService.createCertificate(currentTeacherId(), request));
    }

    @PutMapping("/certificates/{id}")
    public ApiResponse<Certificate> updateCertificate(
            @PathVariable Long id,
            @Valid @RequestBody CertificateRequest request) {
        return ApiResponse.ok(achievementService.updateCertificate(currentTeacherId(), id, request));
    }

    @DeleteMapping("/certificates/{id}")
    public ApiResponse<Void> deleteCertificate(@PathVariable Long id) {
        achievementService.deleteCertificate(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
