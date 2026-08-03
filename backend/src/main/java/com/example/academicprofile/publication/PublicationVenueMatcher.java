package com.example.academicprofile.publication;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PublicationVenueMatcher {
    private static final double FUZZY_THRESHOLD = 0.82d;

    public List<Match> match(String query, List<PublicationVenueReference> references, int limit) {
        String trimmed = query == null ? "" : query.trim();
        String normalized = PublicationVenueNormalizer.normalize(trimmed);
        List<Match> exact = new ArrayList<>();
        List<Match> fuzzy = new ArrayList<>();
        for (PublicationVenueReference ref : references) {
            String method = exactMethod(trimmed, normalized, ref);
            if (method != null) {
                exact.add(new Match(ref, method, 1d));
                continue;
            }
            double score = similarity(normalized, ref.getNormalizedName());
            if (score >= FUZZY_THRESHOLD) fuzzy.add(new Match(ref, "FUZZY_CANDIDATE", score));
        }
        if (!exact.isEmpty()) {
            boolean conflict = exact.size() > 1;
            return exact.stream().limit(limit)
                    .map(item -> item.withConfirmationRequired(conflict))
                    .toList();
        }
        return fuzzy.stream()
                .sorted(Comparator.comparingDouble(Match::score).reversed()
                        .thenComparing(item -> item.reference().getStandardName()))
                .limit(limit)
                .map(item -> item.withConfirmationRequired(true))
                .toList();
    }

    public String confirmationMethod(String venueName, PublicationVenueReference reference) {
        String exact = exactMethod(venueName == null ? "" : venueName.trim(),
                PublicationVenueNormalizer.normalize(venueName), reference);
        return exact == null || "NORMALIZED_NAME".equals(exact) ? "USER_CONFIRMED" : exact;
    }

    private String exactMethod(String raw, String normalized, PublicationVenueReference ref) {
        if (raw.equalsIgnoreCase(ref.getStandardName())) return "EXACT_NAME";
        if (ref.getAbbreviation() != null && raw.equalsIgnoreCase(ref.getAbbreviation())) return "EXACT_ABBR";
        if (!normalized.isEmpty() && normalized.equals(ref.getNormalizedName())) return "NORMALIZED_NAME";
        if (ref.getNormalizedAbbreviation() != null && normalized.equals(ref.getNormalizedAbbreviation())) return "EXACT_ABBR";
        if (ref.getAliases() != null) {
            for (String alias : ref.getAliases().split("\\|")) {
                if (normalized.equals(PublicationVenueNormalizer.normalize(alias))) return "ALIAS";
            }
        }
        return null;
    }

    private double similarity(String left, String right) {
        if (left.isEmpty() || right == null || right.isEmpty()) return 0d;
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return 1d - ((double) previous[right.length()] / Math.max(left.length(), right.length()));
    }

    public record Match(
            PublicationVenueReference reference,
            String method,
            double score,
            boolean confirmationRequired) {
        Match(PublicationVenueReference reference, String method, double score) {
            this(reference, method, score, false);
        }
        Match withConfirmationRequired(boolean required) {
            return new Match(reference, method, score, required);
        }
    }
}
