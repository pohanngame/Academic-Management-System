package com.example.academicprofile.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.academicprofile.achievement.Paper;

class PaperRatingVisibilityTest {
    @Test
    void suggestedMetricIsNotVerifiedForExportOrPublicProfile() {
        Paper paper = new Paper();
        paper.setMetricStatus("SUGGESTED");
        assertThat(PaperRatingVisibility.hasVerifiedMetric(paper)).isFalse();
        paper.setMetricStatus("MANUAL");
        assertThat(PaperRatingVisibility.hasVerifiedMetric(paper)).isTrue();
        paper.setMetricStatus("USER_CONFIRMED");
        assertThat(PaperRatingVisibility.hasVerifiedMetric(paper)).isTrue();
    }

    @Test
    void aiMapDropsUnconfirmedCcfAndSuggestedJcrValues() {
        Map<String, Object> row = new HashMap<>();
        row.put("ccfRank", "A");
        row.put("jcrQuartile", "Q1");
        row.put("journalImpactFactor", 9.9);
        row.put("metricStatus", "SUGGESTED");
        PaperRatingVisibility.removeUnverifiedFromAiMap(row);
        assertThat(row).doesNotContainKeys("ccfRank", "jcrQuartile", "journalImpactFactor");
    }
}
