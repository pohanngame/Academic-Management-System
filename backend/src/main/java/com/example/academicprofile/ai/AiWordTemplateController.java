package com.example.academicprofile.ai;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;
import com.example.academicprofile.word.WordTemplateValidationException;
import com.example.academicprofile.word.WordTemplateValidationResult;

@RestController
@RequestMapping("/api/ai/templates")
public class AiWordTemplateController {

    private final AiWordTemplateService service;

    public AiWordTemplateController(AiWordTemplateService service) {
        this.service = service;
    }

    @PostMapping(value = "/custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AiWordTemplateUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean appendMissingPlaceholder) {
        return ApiResponse.ok(service.upload(
                SecurityUtils.currentUser().teacherId(), file, appendMissingPlaceholder));
    }

    @GetMapping("/example")
    public ResponseEntity<ByteArrayResource> example() {
        return download(service.example());
    }

    @GetMapping("/test")
    public ResponseEntity<ByteArrayResource> test(
            @RequestParam String templateType,
            @RequestParam(required = false) Long templateFileId) {
        return download(service.test(
                SecurityUtils.currentUser().teacherId(), templateType, templateFileId));
    }

    @ExceptionHandler(WordTemplateValidationException.class)
    public ResponseEntity<ApiResponse<AiWordTemplateValidationResponse>> validationFailure(
            WordTemplateValidationException exception) {
        WordTemplateValidationResult result = new WordTemplateValidationResult(
                exception.code(), exception.getMessage(), exception.validPlaceholderCount(),
                "未指定", false);
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiResponse<>(false, exception.getMessage(),
                        AiWordTemplateValidationResponse.from(result)));
    }

    private ResponseEntity<ByteArrayResource> download(AiGeneratedFile file) {
        String encodedName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new ByteArrayResource(file.content()));
    }
}
