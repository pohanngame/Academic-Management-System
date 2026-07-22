package com.example.academicprofile.file;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

@RestController
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(value = "/api/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileMetadataResponse> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam FileBusinessType businessType,
            @RequestParam(required = false) Long businessId) {
        FileMetadata metadata = fileStorageService.uploadFile(currentTeacherId(), businessType, businessId, file);
        return ApiResponse.ok(FileMetadataResponse.from(metadata));
    }

    @PostMapping(value = "/api/teacher/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileMetadataResponse> uploadAvatar(@RequestPart("file") MultipartFile file) {
        FileMetadata metadata = fileStorageService.uploadAvatar(currentTeacherId(), file);
        return ApiResponse.ok(FileMetadataResponse.from(metadata));
    }

    @GetMapping("/api/files")
    public ApiResponse<List<FileMetadataResponse>> listFiles(
            @RequestParam FileBusinessType businessType,
            @RequestParam(required = false) Long businessId) {
        return ApiResponse.ok(fileStorageService.listFiles(currentTeacherId(), businessType, businessId)
                .stream()
                .map(FileMetadataResponse::from)
                .toList());
    }

    @GetMapping("/api/files/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        FileDownload download = fileStorageService.downloadFile(currentTeacherId(), id);
        String encodedName = URLEncoder.encode(download.metadata().getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(download.resource());
    }

    @DeleteMapping("/api/files/{id}")
    public ApiResponse<Void> deleteFile(@PathVariable Long id) {
        fileStorageService.deleteFile(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }
}
