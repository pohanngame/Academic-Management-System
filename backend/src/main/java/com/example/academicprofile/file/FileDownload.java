package com.example.academicprofile.file;

import org.springframework.core.io.Resource;

public record FileDownload(FileMetadata metadata, Resource resource) {
}
