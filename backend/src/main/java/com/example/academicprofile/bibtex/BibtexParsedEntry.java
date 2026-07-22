package com.example.academicprofile.bibtex;

public record BibtexParsedEntry(
        String rawEntry,
        String entryType,
        String bibKey,
        String title,
        String authors,
        String journal,
        String booktitle,
        Integer year,
        String doi,
        String volume,
        String number,
        String pages,
        String publisher,
        String url,
        String abstractText,
        String keywords,
        String errorMessage) {

    public boolean failed() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
