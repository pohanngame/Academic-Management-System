package com.example.academicprofile.file;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;

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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.access.AccessPermission;
import com.example.academicprofile.access.ResourceAccessAuditService;
import com.example.academicprofile.access.ResourceType;
import com.example.academicprofile.security.CurrentUser;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
@RestController
public class FileController {

    private final FileStorageService fileStorageService;
    private final ResourceAccessAuditService accessAudits;

    public FileController(FileStorageService fileStorageService, ResourceAccessAuditService accessAudits) {
        this.fileStorageService = fileStorageService;
        this.accessAudits = accessAudits;
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

    @GetMapping("/api/files/shared")
    public ApiResponse<List<FileMetadataResponse>> listSharedFiles(
            @RequestParam FileBusinessType businessType,
            @RequestParam Long businessId,
            @RequestParam(defaultValue = "VIEW") AccessPermission permission) {
        return ApiResponse.ok(fileStorageService.listSharedFiles(
                        SecurityUtils.currentUser(), businessType, businessId, permission)
                .stream()
                .map(FileMetadataResponse::from)
                .toList());
    }

    @GetMapping("/api/files/attachments")
    public ApiResponse<List<FileMetadataResponse>> listOwnedAttachments() {
        return ApiResponse.ok(fileStorageService.listOwnedAttachments(currentTeacherId())
                .stream().map(FileMetadataResponse::from).toList());
    }

    @GetMapping("/api/files/attachments/page")
    public ApiResponse<Page<FileMetadataResponse>> pageOwnedAttachments(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FileMetadata> source =
                fileStorageService.pageOwnedAttachments(currentTeacherId(), search, page, size);
        Page<FileMetadataResponse> response =
                Page.of(source.getCurrent(), source.getSize(), source.getTotal());
        response.setRecords(source.getRecords().stream().map(FileMetadataResponse::from).toList());
        return ApiResponse.ok(response);
    }

    @GetMapping("/api/files/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable Long id, HttpServletRequest request) {
        AuthorizedStream stream = authorizedStream(
                id, AccessPermission.DOWNLOAD, "DOWNLOAD", request);
        FileDownload download = stream.authorized().download();
        String encodedName = URLEncoder.encode(download.metadata().getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(stream.body());
    }

    @GetMapping("/api/files/{id}/preview")
    public ResponseEntity<StreamingResponseBody> previewFile(
            @PathVariable Long id, HttpServletRequest request) {
        AuthorizedStream stream = authorizedStream(
                id, AccessPermission.VIEW, "PREVIEW", request);
        FileDownload download = stream.authorized().download();
        String encodedName = URLEncoder.encode(download.metadata().getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(stream.body());
    }

    @DeleteMapping("/api/files/{id}")
    public ApiResponse<Void> deleteFile(@PathVariable Long id) {
        fileStorageService.deleteFile(currentTeacherId(), id);
        return ApiResponse.ok(null);
    }

    private Long currentTeacherId() {
        return SecurityUtils.currentUser().teacherId();
    }

    private AuthorizedStream authorizedStream(
            Long fileId, AccessPermission permission, String operation, HttpServletRequest request) {
        CurrentUser actor = SecurityUtils.currentUser();
        FileStorageService.AuthorizedFileDownload authorized;
        try {
            authorized = fileStorageService.readFile(actor, fileId, permission);
        } catch (RuntimeException ex) {
            try {
                FileStorageService.FileAuditTarget target =
                        fileStorageService.findAuditTarget(fileId);
                if (target != null) {
                    accessAudits.record(
                            actor, target.ownerTeacherId(), operation,
                            target.resourceType(), target.resourceId(), target.fileId(),
                            null, false, request.getRemoteAddr(),
                            request.getHeader("User-Agent"));
                }
            } catch (RuntimeException ignored) {
                // Preserve the original controlled authorization or file error.
            }
            throw ex;
        }
        FileMetadata metadata = authorized.download().metadata();
        String ip = request.getRemoteAddr();
        String client = request.getHeader("User-Agent");
        StreamingResponseBody body = output -> {
            boolean success = false;
            try (InputStream input = authorized.download().resource().getInputStream()) {
                input.transferTo(output);
                output.flush();
                success = true;
            } finally {
                try {
                    ResourceType type = metadata.getBusinessId() == null
                            ? null
                            : ResourceType.fromFileBusinessType(metadata.getBusinessType());
                    accessAudits.record(actor, metadata.getTeacherId(), operation, type,
                            metadata.getBusinessId(), metadata.getId(),
                            authorized.decision() == null ? null : authorized.decision().grant(),
                            success, ip, client);
                } catch (RuntimeException auditFailure) {
                    if (success) {
                        throw new IOException("File access audit could not be completed");
                    }
                }
            }
        };
        return new AuthorizedStream(authorized, body);
    }

    private record AuthorizedStream(
            FileStorageService.AuthorizedFileDownload authorized,
            StreamingResponseBody body) {
    }
}
