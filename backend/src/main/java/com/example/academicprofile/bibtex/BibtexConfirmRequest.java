package com.example.academicprofile.bibtex;

import java.util.List;

public record BibtexConfirmRequest(
        List<Long> itemIds,
        Boolean forceDuplicates) {
}
