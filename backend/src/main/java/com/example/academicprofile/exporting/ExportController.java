package com.example.academicprofile.exporting;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/fields")
    public ApiResponse<List<ExportModuleDefinition>> fields() {
        return ApiResponse.ok(exportService.getFieldDefinitions());
    }

    @PostMapping("/excel")
    public ResponseEntity<ByteArrayResource> exportExcel(@Valid @RequestBody ExportRequest request) {
        ExportFile exportFile = exportService.exportExcel(currentTeacherId(), request);
        return fileResponse(exportFile);
    }

    @PostMapping("/word")
    public ResponseEntity<ByteArrayResource> exportWord(@Valid @RequestBody ExportRequest request) {
        ExportFile exportFile = exportService.exportWord(currentTeacherId(), request);
        return fileResponse(exportFile);
    }

    @GetMapping("/templates")
    public ApiResponse<List<ExportTemplateResponse>> listTemplates() {
        return ApiResponse.ok(exportService.listTemplates(currentTeacherId()));
    }

    @PostMapping("/templates")
    public ApiResponse<ExportTemplateResponse> saveTemplate(@Valid @RequestBody ExportTemplateRequest request) {
        return ApiResponse.ok(exportService.saveTemplate(currentTeacherId(), request));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        exportService.deleteTemplate(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    private ResponseEntity<ByteArrayResource> fileResponse(ExportFile exportFile) {
        String encodedName = URLEncoder.encode(exportFile.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportFile.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new ByteArrayResource(exportFile.content()));
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
