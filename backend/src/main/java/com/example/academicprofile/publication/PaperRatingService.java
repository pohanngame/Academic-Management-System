package com.example.academicprofile.publication;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperRequest;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class PaperRatingService {
    private static final Set<String> CCF_RANKS = Set.of("A", "B", "C");
    private final PublicationVenueReferenceMapper references;
    private final PublicationVenueDatasetMapper datasets;
    private final JournalMetricReferenceMapper metricReferences;
    private final PublicationVenueMatcher matcher;

    public PaperRatingService(
            PublicationVenueReferenceMapper references,
            PublicationVenueDatasetMapper datasets,
            JournalMetricReferenceMapper metricReferences,
            PublicationVenueMatcher matcher) {
        this.references = references;
        this.datasets = datasets;
        this.metricReferences = metricReferences;
        this.matcher = matcher;
    }

    public void apply(Paper paper, PaperRequest request) {
        VenueType venueType = parseVenueType(request.venueType());
        paper.setVenueType(venueType.name());
        paper.setVenueAbbreviation(text(request.venueAbbreviation(), 128));
        applyCcf(paper, request, venueType);
        applyJournalMetrics(paper, request, venueType);
    }

    private void applyCcf(Paper paper, PaperRequest request, VenueType venueType) {
        clearCcf(paper);
        if (request.ccfReferenceId() != null) {
            if (!Boolean.TRUE.equals(request.ccfConfirmed())) {
                throw new BusinessException("保存 CCF 匹配结果前请先确认");
            }
            confirmReference(paper, request.ccfReferenceId(), venueType, request.publicationName());
            return;
        }
        if (StringUtils.hasText(request.manualCcfRank())
                || request.manualCcfCategoryId() != null
                || StringUtils.hasText(request.manualCcfCategoryName())) {
            if (!Boolean.TRUE.equals(request.ccfConfirmed())) {
                throw new BusinessException("手工 CCF 信息必须由用户确认");
            }
            String rank = request.manualCcfRank() == null
                    ? null : request.manualCcfRank().trim().toUpperCase(Locale.ROOT);
            if (!CCF_RANKS.contains(rank) || request.manualCcfCategoryId() == null
                    || !StringUtils.hasText(request.manualCcfCategoryName())) {
                throw new BusinessException("手工 CCF 信息不完整或等级无效");
            }
            paper.setCcfRank(rank);
            paper.setCcfCategoryId(request.manualCcfCategoryId());
            paper.setCcfCategoryName(text(request.manualCcfCategoryName(), 255));
            paper.setCcfDataYear(2026);
            paper.setCcfSource("MANUAL_USER_ENTRY");
            paper.setCcfMatchMethod("MANUAL");
            paper.setCcfConfirmedAt(LocalDateTime.now());
        }
    }

    public void confirmReference(Paper paper, Long referenceId, VenueType venueType, String venueName) {
        PublicationVenueReference reference = references.selectById(referenceId);
        if (reference == null || venueType == VenueType.UNKNOWN
                || !venueType.name().equals(reference.getVenueType())) {
            throw new BusinessException("CCF 参考数据不存在或与发表类型不匹配");
        }
        if (matcher.match(venueName, java.util.List.of(reference), 1).isEmpty()) {
            throw new BusinessException("CCF 参考项与当前期刊或会议名称不匹配");
        }
        PublicationVenueDataset dataset = datasets.selectById(reference.getDatasetId());
        if (dataset == null || !"CCF_CUSTOMER_PROVIDED_2026".equals(dataset.getDatasetKey())) {
            throw new BusinessException("CCF 参考数据版本无效");
        }
        paper.setCcfReferenceId(reference.getId());
        paper.setCcfRank(reference.getCcfRank());
        paper.setCcfCategoryId(reference.getCategoryId());
        paper.setCcfCategoryName(reference.getCategoryName());
        paper.setCcfDataYear(dataset.getDataYear());
        paper.setCcfSource(dataset.getDatasetKey());
        paper.setCcfMatchMethod(matcher.confirmationMethod(venueName, reference));
        paper.setCcfConfirmedAt(LocalDateTime.now());
    }

    private void applyJournalMetrics(Paper paper, PaperRequest request, VenueType venueType) {
        if (request.journalMetricReferenceId() != null) {
            applyConfirmedMetricReference(paper, request, venueType);
            return;
        }
        String issn = text(request.issn(), 16);
        String eissn = text(request.eissn(), 16);
        String subject = text(request.jcrSubject(), 255);
        String description = text(request.metricSourceDescription(), 500);
        String sourceUrl = text(request.metricSourceUrl(), 1024);
        boolean hasJournalData = issn != null || eissn != null || request.jcrDataYear() != null
                || request.journalImpactFactor() != null || request.jcrQuartile() != null
                || subject != null || description != null || sourceUrl != null;
        if (venueType != VenueType.JOURNAL && hasJournalData) {
            throw new BusinessException("会议论文不能填写 ISSN、JCR 分区或影响因子");
        }
        try {
            PaperRatingValidator.validateJcr(venueType, request.jcrDataYear(),
                    request.journalImpactFactor(), normalizedQuartile(request.jcrQuartile()));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        if (venueType != VenueType.JOURNAL) {
            clearJournalMetrics(paper);
            return;
        }
        paper.setIssn(issn);
        paper.setEissn(eissn);
        paper.setJcrDataYear(request.jcrDataYear());
        paper.setJournalImpactFactor(request.journalImpactFactor());
        paper.setJcrQuartile(normalizedQuartile(request.jcrQuartile()));
        paper.setJcrSubject(subject);
        paper.setMetricSourceDescription(description);
        paper.setMetricSourceUrl(sourceUrl);
        boolean hasMetricSnapshot = request.jcrDataYear() != null || request.journalImpactFactor() != null
                || request.jcrQuartile() != null || subject != null || description != null || sourceUrl != null;
        paper.setMetricStatus(hasMetricSnapshot ? "MANUAL" : null);
        paper.setMetricConfirmedAt(hasMetricSnapshot ? LocalDateTime.now() : null);
    }

    private void applyConfirmedMetricReference(Paper paper, PaperRequest request, VenueType venueType) {
        if (venueType != VenueType.JOURNAL || !Boolean.TRUE.equals(request.metricConfirmed())) {
            throw new BusinessException("非官方 JCR 建议必须由期刊所有人明确核实确认");
        }
        JournalMetricReference reference = metricReferences.selectById(request.journalMetricReferenceId());
        if (reference == null || !Boolean.TRUE.equals(reference.getActive())) {
            throw new BusinessException("JCR 建议数据不存在或已失效");
        }
        Integer year = request.jcrDataYear() == null ? reference.getDataYear() : request.jcrDataYear();
        java.math.BigDecimal impact = request.journalImpactFactor() == null
                ? reference.getImpactFactor() : request.journalImpactFactor();
        String quartile = normalizedQuartile(request.jcrQuartile()) == null
                ? reference.getJcrQuartile() : normalizedQuartile(request.jcrQuartile());
        try {
            PaperRatingValidator.validateJcr(venueType, year, impact, quartile);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("JCR 建议数据无效");
        }
        paper.setIssn(reference.getIssn());
        paper.setEissn(reference.getEissn());
        paper.setJcrDataYear(year);
        paper.setJournalImpactFactor(impact);
        paper.setJcrQuartile(quartile);
        paper.setJcrSubject(StringUtils.hasText(request.jcrSubject())
                ? text(request.jcrSubject(), 255) : reference.getSubjectName());
        paper.setMetricSourceDescription(reference.getSourceName());
        paper.setMetricSourceUrl(reference.getSourceUrl());
        paper.setMetricStatus("USER_CONFIRMED");
        paper.setMetricConfirmedAt(LocalDateTime.now());
    }

    private VenueType parseVenueType(String value) {
        if (!StringUtils.hasText(value)) return VenueType.UNKNOWN;
        try {
            return VenueType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("发表载体类型无效");
        }
    }

    private String normalizedQuartile(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String text(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new BusinessException("评级字段长度超出限制");
        return trimmed;
    }

    private void clearCcf(Paper paper) {
        paper.setCcfReferenceId(null);
        paper.setCcfRank(null);
        paper.setCcfCategoryId(null);
        paper.setCcfCategoryName(null);
        paper.setCcfDataYear(null);
        paper.setCcfSource(null);
        paper.setCcfMatchMethod(null);
        paper.setCcfConfirmedAt(null);
    }

    private void clearJournalMetrics(Paper paper) {
        paper.setIssn(null);
        paper.setEissn(null);
        paper.setJcrDataYear(null);
        paper.setJournalImpactFactor(null);
        paper.setJcrQuartile(null);
        paper.setJcrSubject(null);
        paper.setMetricSourceDescription(null);
        paper.setMetricSourceUrl(null);
        paper.setMetricStatus(null);
        paper.setMetricConfirmedAt(null);
    }
}
