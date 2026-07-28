package com.example.academicprofile.backup;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/restores/staged")
public class RestoreStagingController {

    private final RestoreStagingService staging;

    public RestoreStagingController(RestoreStagingService staging) {
        this.staging = staging;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RestoreStageMetadata> stage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("currentPassword") String currentPassword) {
        return ApiResponse.ok(staging.stage(SecurityUtils.currentUser(), file, currentPassword));
    }

    @GetMapping
    public ApiResponse<List<RestoreStageMetadata>> list() {
        return ApiResponse.ok(staging.list());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable String id,
            @Valid @RequestBody RestorePasswordRequest request) {
        staging.delete(SecurityUtils.currentUser(), id, request);
        return ApiResponse.ok(null);
    }
}
