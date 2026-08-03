package com.example.academicprofile.publication;

import java.util.List;
import java.util.Map;

import com.example.academicprofile.achievement.Paper;

public final class PaperRatingVisibility {
    private PaperRatingVisibility() {
    }

    public static boolean hasVerifiedMetric(Paper paper) {
        return paper != null && isVerifiedStatus(paper.getMetricStatus());
    }

    public static void removeUnverifiedFromAiMap(Map<String, Object> row) {
        if (row.get("ccfConfirmedAt") == null) {
            for (String key : List.of("ccfRank", "ccfCategoryName", "ccfDataYear")) row.remove(key);
        }
        if (!isVerifiedStatus(row.get("metricStatus"))) {
            for (String key : List.of("jcrDataYear", "journalImpactFactor", "jcrQuartile", "jcrSubject")) {
                row.remove(key);
            }
        }
    }

    private static boolean isVerifiedStatus(Object status) {
        return "MANUAL".equals(status) || "USER_CONFIRMED".equals(status);
    }
}
