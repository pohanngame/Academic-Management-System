package com.example.academicprofile.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PublicationVenueMatcherTest {
    private final PublicationVenueMatcher matcher = new PublicationVenueMatcher();

    @Test
    void separatesVenueTypesBeforeMatchingAndExplainsExactMatches() {
        PublicationVenueReference journal = ref("JOURNAL", "IEEE Transactions on Computers", "TC");
        assertEquals("EXACT_NAME", matcher.match("IEEE Transactions on Computers", List.of(journal), 20).get(0).method());
        assertEquals("EXACT_ABBR", matcher.match("tc", List.of(journal), 20).get(0).method());
    }

    @Test
    void normalizesCaseWhitespacePunctuationAndAmpersandConservatively() {
        PublicationVenueReference ref = ref("CONFERENCE", "Research & Development Conference", "RDC");
        assertEquals("NORMALIZED_NAME",
                matcher.match("  research and development conference  ", List.of(ref), 20).get(0).method());
    }

    @Test
    void fuzzyAndConflictingExactMatchesAlwaysRequireConfirmation() {
        PublicationVenueReference first = ref("JOURNAL", "Transactions on Computing Systems", "TCS");
        PublicationVenueReference second = ref("JOURNAL", "Theory of Computing Systems", "TCS");
        assertTrue(matcher.match("TCS", List.of(first, second), 20).stream()
                .allMatch(PublicationVenueMatcher.Match::confirmationRequired));
        assertTrue(matcher.match("Transactions on Computing System", List.of(first), 20).get(0)
                .confirmationRequired());
    }

    private PublicationVenueReference ref(String type, String name, String abbreviation) {
        PublicationVenueReference ref = new PublicationVenueReference();
        ref.setVenueType(type);
        ref.setStandardName(name);
        ref.setNormalizedName(PublicationVenueNormalizer.normalize(name));
        ref.setAbbreviation(abbreviation);
        ref.setNormalizedAbbreviation(PublicationVenueNormalizer.normalize(abbreviation));
        return ref;
    }
}
