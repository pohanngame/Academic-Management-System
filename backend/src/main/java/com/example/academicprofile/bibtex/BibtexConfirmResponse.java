package com.example.academicprofile.bibtex;

public record BibtexConfirmResponse(
        int importedCount,
        int skippedCount) {
}
