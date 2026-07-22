package com.example.academicprofile.ocr;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/api/ocr/tasks")
    public ApiResponse<OcrTaskResponse> createTask(@Valid @RequestBody OcrTaskRequest request) {
        return ApiResponse.ok(ocrService.createTask(currentTeacherId(), request));
    }

    @GetMapping("/api/ocr/tasks")
    public ApiResponse<List<OcrTaskResponse>> listTasks() {
        return ApiResponse.ok(ocrService.listTasks(currentTeacherId()));
    }

    @GetMapping("/api/ocr/tasks/{id}")
    public ApiResponse<OcrTaskResponse> getTask(@PathVariable Long id) {
        return ApiResponse.ok(ocrService.getTask(currentTeacherId(), id));
    }

    @PutMapping("/api/ocr/results/{id}")
    public ApiResponse<OcrResultResponse> updateResult(
            @PathVariable Long id,
            @Valid @RequestBody OcrResultRequest request) {
        return ApiResponse.ok(ocrService.updateResult(currentTeacherId(), id, request));
    }

    @DeleteMapping("/api/ocr/results/{id}")
    public ApiResponse<Void> ignoreResult(@PathVariable Long id) {
        ocrService.ignoreResult(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/ocr/tasks/{id}/confirm")
    public ApiResponse<OcrConfirmResponse> confirmTask(
            @PathVariable Long id,
            @RequestBody OcrConfirmRequest request) {
        return ApiResponse.ok(ocrService.confirmTask(currentTeacherId(), id, request));
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
