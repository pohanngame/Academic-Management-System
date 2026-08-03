package com.example.academicprofile.publication;

import java.text.Normalizer;
import java.util.Locale;

public final class PublicationVenueNormalizer {
    private PublicationVenueNormalizer() {}

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[\\p{Punct}&&[^-]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }
}
