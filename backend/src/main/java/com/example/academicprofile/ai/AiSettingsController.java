package com.example.academicprofile.ai;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai/settings")
public class AiSettingsController {

    private final AiSettingsService settingsService;

    public AiSettingsController(AiSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<AiSettingsResponse> getSettings() {
        return ApiResponse.ok(settingsService.getSettings(currentTeacherId()));
    }

    @PutMapping
    public ApiResponse<AiSettingsResponse> saveSettings(@Valid @RequestBody AiSettingsRequest request) {
        return ApiResponse.ok(settingsService.saveSettings(currentTeacherId(), request));
    }

    @PostMapping("/test")
    public ApiResponse<AiConnectionTestResult> testConnection() {
        return ApiResponse.ok(settingsService.testConnection(currentTeacherId()));
    }

    @DeleteMapping
    public ApiResponse<Void> deleteSettings() {
        settingsService.deleteSettings(currentTeacherId());
        return ApiResponse.ok(null);
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
