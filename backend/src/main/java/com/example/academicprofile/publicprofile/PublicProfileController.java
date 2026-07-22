package com.example.academicprofile.publicprofile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.file.FileDownload;
import com.example.academicprofile.file.FileStorageService;

@RestController
public class PublicProfileController {

    private final PublicProfileService publicProfileService;
    private final FileStorageService fileStorageService;

    public PublicProfileController(PublicProfileService publicProfileService, FileStorageService fileStorageService) {
        this.publicProfileService = publicProfileService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/api/public/profiles/{slug}")
    public ApiResponse<PublicProfileResponse> getPublicProfile(@PathVariable String slug) {
        return ApiResponse.ok(publicProfileService.getBySlug(slug));
    }

    @GetMapping("/api/public/avatars/{id}")
    public ResponseEntity<Resource> downloadPublicAvatar(@PathVariable Long id) {
        FileDownload download = fileStorageService.downloadPublicAvatar(id);
        String encodedName = URLEncoder.encode(download.metadata().getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(download.resource());
    }
}
