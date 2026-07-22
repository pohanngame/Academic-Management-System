package com.example.academicprofile.profileblock;

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
public class ProfileBlockController {

    private final ProfileBlockService profileBlockService;

    public ProfileBlockController(ProfileBlockService profileBlockService) {
        this.profileBlockService = profileBlockService;
    }

    @GetMapping("/academic-qualifications")
    public ApiResponse<List<AcademicQualification>> listAcademicQualifications() {
        return ApiResponse.ok(profileBlockService.listAcademicQualifications(currentTeacherId()));
    }

    @PostMapping("/academic-qualifications")
    public ApiResponse<AcademicQualification> createAcademicQualification(
            @Valid @RequestBody AcademicQualificationRequest request) {
        return ApiResponse.ok(profileBlockService.createAcademicQualification(currentTeacherId(), request));
    }

    @PutMapping("/academic-qualifications/{id}")
    public ApiResponse<AcademicQualification> updateAcademicQualification(
            @PathVariable Long id,
            @Valid @RequestBody AcademicQualificationRequest request) {
        return ApiResponse.ok(profileBlockService.updateAcademicQualification(currentTeacherId(), id, request));
    }

    @DeleteMapping("/academic-qualifications/{id}")
    public ApiResponse<Void> deleteAcademicQualification(@PathVariable Long id) {
        profileBlockService.deleteAcademicQualification(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/teaching-areas")
    public ApiResponse<List<TeachingArea>> listTeachingAreas() {
        return ApiResponse.ok(profileBlockService.listTeachingAreas(currentTeacherId()));
    }

    @PostMapping("/teaching-areas")
    public ApiResponse<TeachingArea> createTeachingArea(@Valid @RequestBody AreaBlockRequest request) {
        return ApiResponse.ok(profileBlockService.createTeachingArea(currentTeacherId(), request));
    }

    @PutMapping("/teaching-areas/{id}")
    public ApiResponse<TeachingArea> updateTeachingArea(
            @PathVariable Long id,
            @Valid @RequestBody AreaBlockRequest request) {
        return ApiResponse.ok(profileBlockService.updateTeachingArea(currentTeacherId(), id, request));
    }

    @DeleteMapping("/teaching-areas/{id}")
    public ApiResponse<Void> deleteTeachingArea(@PathVariable Long id) {
        profileBlockService.deleteTeachingArea(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/research-areas")
    public ApiResponse<List<ResearchArea>> listResearchAreas() {
        return ApiResponse.ok(profileBlockService.listResearchAreas(currentTeacherId()));
    }

    @PostMapping("/research-areas")
    public ApiResponse<ResearchArea> createResearchArea(@Valid @RequestBody AreaBlockRequest request) {
        return ApiResponse.ok(profileBlockService.createResearchArea(currentTeacherId(), request));
    }

    @PutMapping("/research-areas/{id}")
    public ApiResponse<ResearchArea> updateResearchArea(
            @PathVariable Long id,
            @Valid @RequestBody AreaBlockRequest request) {
        return ApiResponse.ok(profileBlockService.updateResearchArea(currentTeacherId(), id, request));
    }

    @DeleteMapping("/research-areas/{id}")
    public ApiResponse<Void> deleteResearchArea(@PathVariable Long id) {
        profileBlockService.deleteResearchArea(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/professional-services")
    public ApiResponse<List<ProfessionalService>> listProfessionalServices() {
        return ApiResponse.ok(profileBlockService.listProfessionalServices(currentTeacherId()));
    }

    @PostMapping("/professional-services")
    public ApiResponse<ProfessionalService> createProfessionalService(
            @Valid @RequestBody ProfessionalServiceRequest request) {
        return ApiResponse.ok(profileBlockService.createProfessionalService(currentTeacherId(), request));
    }

    @PutMapping("/professional-services/{id}")
    public ApiResponse<ProfessionalService> updateProfessionalService(
            @PathVariable Long id,
            @Valid @RequestBody ProfessionalServiceRequest request) {
        return ApiResponse.ok(profileBlockService.updateProfessionalService(currentTeacherId(), id, request));
    }

    @DeleteMapping("/professional-services/{id}")
    public ApiResponse<Void> deleteProfessionalService(@PathVariable Long id) {
        profileBlockService.deleteProfessionalService(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/working-experiences")
    public ApiResponse<List<WorkingExperience>> listWorkingExperiences() {
        return ApiResponse.ok(profileBlockService.listWorkingExperiences(currentTeacherId()));
    }

    @PostMapping("/working-experiences")
    public ApiResponse<WorkingExperience> createWorkingExperience(
            @Valid @RequestBody WorkingExperienceRequest request) {
        return ApiResponse.ok(profileBlockService.createWorkingExperience(currentTeacherId(), request));
    }

    @PutMapping("/working-experiences/{id}")
    public ApiResponse<WorkingExperience> updateWorkingExperience(
            @PathVariable Long id,
            @Valid @RequestBody WorkingExperienceRequest request) {
        return ApiResponse.ok(profileBlockService.updateWorkingExperience(currentTeacherId(), id, request));
    }

    @DeleteMapping("/working-experiences/{id}")
    public ApiResponse<Void> deleteWorkingExperience(@PathVariable Long id) {
        profileBlockService.deleteWorkingExperience(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
