package com.example.academicprofile.bibtex;

public record BibtexImportItemResponse(
        Long id,
        Long taskId,
        String status,
        String errorMessage,
        String duplicateStatus,
        Long duplicatePaperId,
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
        Boolean selected,
        Long createdPaperId) {

    public static BibtexImportItemResponse from(BibtexImportItem item) {
        return new BibtexImportItemResponse(
                item.getId(),
                item.getTaskId(),
                item.getStatus(),
                item.getErrorMessage(),
                item.getDuplicateStatus(),
                item.getDuplicatePaperId(),
                item.getEntryType(),
                item.getBibKey(),
                item.getTitle(),
                item.getAuthors(),
                item.getJournal(),
                item.getBooktitle(),
                item.getYear(),
                item.getDoi(),
                item.getVolume(),
                item.getNumber(),
                item.getPages(),
                item.getPublisher(),
                item.getUrl(),
                item.getAbstractText(),
                item.getKeywords(),
                item.getSelected(),
                item.getCreatedPaperId());
    }
}
