package com.example.academicprofile.bibtex;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record BibtexImportItemRequest(
        @Size(max = 64) String entryType,
        @Size(max = 255) String bibKey,
        @Size(max = 500) String title,
        @Size(max = 5000) String authors,
        @Size(max = 255) String journal,
        @Size(max = 255) String booktitle,
        @Min(0) @Max(9999) Integer year,
        @Size(max = 255) String doi,
        @Size(max = 64) String volume,
        @Size(max = 64) String number,
        @Size(max = 64) String pages,
        @Size(max = 255) String publisher,
        @Size(max = 1024) String url,
        @Size(max = 20000) String abstractText,
        @Size(max = 1000) String keywords,
        Boolean selected) {
}
