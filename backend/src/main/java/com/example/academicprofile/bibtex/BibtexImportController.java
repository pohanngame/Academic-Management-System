package com.example.academicprofile.bibtex;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
public class BibtexImportController {

    private final BibtexImportService bibtexImportService;

    public BibtexImportController(BibtexImportService bibtexImportService) {
        this.bibtexImportService = bibtexImportService;
    }

    @PostMapping("/api/bibtex/import/text")
    public ApiResponse<BibtexImportTaskResponse> importText(@Valid @RequestBody BibtexImportTextRequest request) {
        return ApiResponse.ok(bibtexImportService.importText(currentTeacherId(), request.content()));
    }

    @PostMapping(value = "/api/bibtex/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BibtexImportTaskResponse> importFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(bibtexImportService.importFile(currentTeacherId(), file));
    }

    @GetMapping("/api/bibtex/import-tasks")
    public ApiResponse<List<BibtexImportTaskResponse>> listTasks() {
        return ApiResponse.ok(bibtexImportService.listTasks(currentTeacherId()));
    }

    @GetMapping("/api/bibtex/import-tasks/{id}")
    public ApiResponse<BibtexImportTaskResponse> getTask(@PathVariable Long id) {
        return ApiResponse.ok(bibtexImportService.getTask(currentTeacherId(), id));
    }

    @PutMapping("/api/bibtex/import-items/{id}")
    public ApiResponse<BibtexImportItemResponse> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody BibtexImportItemRequest request) {
        return ApiResponse.ok(bibtexImportService.updateItem(currentTeacherId(), id, request));
    }

    @DeleteMapping("/api/bibtex/import-items/{id}")
    public ApiResponse<Void> ignoreItem(@PathVariable Long id) {
        bibtexImportService.ignoreItem(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/bibtex/import-tasks/{id}/confirm")
    public ApiResponse<BibtexConfirmResponse> confirmTask(
            @PathVariable Long id,
            @RequestBody BibtexConfirmRequest request) {
        return ApiResponse.ok(bibtexImportService.confirmTask(currentTeacherId(), id, request));
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
