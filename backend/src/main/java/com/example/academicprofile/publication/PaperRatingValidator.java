package com.example.academicprofile.publication;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Set;

public final class PaperRatingValidator {
    private static final Set<String> QUARTILES = Set.of("Q1", "Q2", "Q3", "Q4");

    private PaperRatingValidator() {
    }

    public static void validateJcr(VenueType venueType, Integer year, BigDecimal impactFactor, String quartile) {
        boolean hasMetric = year != null || impactFactor != null || quartile != null;
        if (venueType != VenueType.JOURNAL && hasMetric) {
            throw new IllegalArgumentException("会议论文不能填写 JCR 分区或影响因子");
        }
        if (year != null && (year < 1900 || year > Year.now().getValue())) {
            throw new IllegalArgumentException("JCR 数据年度不合法");
        }
        if (impactFactor != null
                && (impactFactor.signum() < 0 || impactFactor.scale() < 0 || impactFactor.scale() > 6
                        || impactFactor.precision() > 10
                        || impactFactor.precision() - impactFactor.scale() > 4)) {
            throw new IllegalArgumentException("影响因子必须是非负且最多保留 6 位小数");
        }
        if (quartile != null && !QUARTILES.contains(quartile)) {
            throw new IllegalArgumentException("JCR 分区不合法");
        }
    }
}
