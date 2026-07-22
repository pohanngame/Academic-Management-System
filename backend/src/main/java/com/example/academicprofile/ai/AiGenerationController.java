package com.example.academicprofile.ai;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/ai")
public class AiGenerationController {

    private final AiGenerationService aiGenerationService;

    public AiGenerationController(AiGenerationService aiGenerationService) {
        this.aiGenerationService = aiGenerationService;
    }

    @GetMapping("/modules")
    public ApiResponse<List<AiModuleDefinition>> modules() {
        return ApiResponse.ok(aiGenerationService.moduleDefinitions());
    }

    @PostMapping("/tasks")
    public ApiResponse<AiGenerationTaskResponse> createTask(@Valid @RequestBody AiGenerationRequest request) {
        return ApiResponse.ok(aiGenerationService.createTask(currentTeacherId(), request));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<AiGenerationTaskResponse>> listTasks() {
        return ApiResponse.ok(aiGenerationService.listTasks(currentTeacherId()));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<AiGenerationTaskResponse> getTask(@PathVariable Long id) {
        return ApiResponse.ok(aiGenerationService.getTask(currentTeacherId(), id));
    }

    @PutMapping("/results/{id}")
    public ApiResponse<AiGenerationResultResponse> updateResult(
            @PathVariable Long id,
            @Valid @RequestBody AiResultUpdateRequest request) {
        return ApiResponse.ok(aiGenerationService.updateResult(currentTeacherId(), id, request));
    }

    @PostMapping("/tasks/{id}/confirm")
    public ApiResponse<AiGenerationTaskResponse> confirmTask(@PathVariable Long id) {
        return ApiResponse.ok(aiGenerationService.confirmTask(currentTeacherId(), id));
    }

    @GetMapping("/tasks/{id}/word")
    public ResponseEntity<ByteArrayResource> exportWord(@PathVariable Long id) {
        return download(aiGenerationService.exportWord(currentTeacherId(), id));
    }

    @GetMapping("/tasks/{id}/pdf")
    public ResponseEntity<ByteArrayResource> exportPdf(@PathVariable Long id) {
        return download(aiGenerationService.exportPdf(currentTeacherId(), id));
    }

    private ResponseEntity<ByteArrayResource> download(AiGeneratedFile file) {
        String encodedName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new ByteArrayResource(file.content()));
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        aiGenerationService.deleteTask(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
