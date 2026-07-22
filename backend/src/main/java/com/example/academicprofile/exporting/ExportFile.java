package com.example.academicprofile.exporting;

public record ExportFile(String fileName, String contentType, byte[] content) {
}
