package com.example.academicprofile.publication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PaperRatingValidatorTest {

    @Test
    void journalAcceptsValidJcrSnapshot() {
        assertDoesNotThrow(() -> PaperRatingValidator.validateJcr(
                VenueType.JOURNAL, 2026, new BigDecimal("12.345678"), "Q1"));
    }

    @Test
    void conferenceRejectsJcrFields() {
        assertThrows(IllegalArgumentException.class, () -> PaperRatingValidator.validateJcr(
                VenueType.CONFERENCE, 2026, new BigDecimal("1.2"), "Q2"));
    }

    @Test
    void rejectsInvalidImpactFactorAndYear() {
        assertThrows(IllegalArgumentException.class, () -> PaperRatingValidator.validateJcr(
                VenueType.JOURNAL, 2026, new BigDecimal("-0.1"), "Q1"));
        assertThrows(IllegalArgumentException.class, () -> PaperRatingValidator.validateJcr(
                VenueType.JOURNAL, 2026, new BigDecimal("1.1234567"), "Q1"));
        assertThrows(IllegalArgumentException.class, () -> PaperRatingValidator.validateJcr(
                VenueType.JOURNAL, 2026, new BigDecimal("1E+5"), "Q1"));
        assertThrows(IllegalArgumentException.class, () -> PaperRatingValidator.validateJcr(
                VenueType.JOURNAL, 1899, new BigDecimal("1.2"), "Q1"));
    }
}
