package com.example.academicprofile.bibtex;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BibtexImportTextRequest(
        @NotBlank @Size(max = 500000) String content) {
}
