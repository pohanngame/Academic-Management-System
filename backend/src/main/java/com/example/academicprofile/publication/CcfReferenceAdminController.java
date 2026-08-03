package com.example.academicprofile.publication;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.academicprofile.common.ApiResponse;
import com.example.academicprofile.security.SecurityUtils;

@RestController
@RequestMapping("/api/admin/reference-data/ccf")
public class CcfReferenceAdminController {
    private final CcfReferenceAdminService service;

    public CcfReferenceAdminController(CcfReferenceAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<CcfReferenceAdminService.ReferenceDataStatus> status() {
        return ApiResponse.ok(service.status());
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CcfReferenceImporter.ImportResult> importData(
            @RequestParam MultipartFile file,
            @RequestParam String currentPassword) {
        return ApiResponse.ok(service.importUpload(SecurityUtils.currentUser(), file, currentPassword));
    }
}
