package com.example.academicprofile.publication;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class PublicationRatingQueryService {
    private final PublicationVenueReferenceMapper venues;
    private final PublicationVenueDatasetMapper datasets;
    private final JournalMetricReferenceMapper metrics;
    private final PublicationVenueMatcher matcher;

    public PublicationRatingQueryService(
            PublicationVenueReferenceMapper venues,
            PublicationVenueDatasetMapper datasets,
            JournalMetricReferenceMapper metrics,
            PublicationVenueMatcher matcher) {
        this.venues = venues;
        this.datasets = datasets;
        this.metrics = metrics;
        this.matcher = matcher;
    }

    public VenueSearchResponse search(String query, VenueType type, int requestedLimit) {
        if (!StringUtils.hasText(query) || query.trim().length() < 2 || query.trim().length() > 500) {
            throw new BusinessException("检索词长度应为 2 至 500 个字符");
        }
        if (type == null || type == VenueType.UNKNOWN) {
            throw new BusinessException("必须选择期刊或会议类型");
        }
        int limit = Math.max(1, Math.min(requestedLimit, 20));
        List<PublicationVenueDataset> available = datasets.selectList(null);
        if (available.isEmpty()) {
            return new VenueSearchResponse(false, "管理员尚未配置CCF参考数据", List.of());
        }
        if (available.size() != 1) {
            throw new IllegalStateException("Publication venue dataset state is inconsistent");
        }
        PublicationVenueDataset dataset = available.get(0);
        List<PublicationVenueReference> candidates = venues.selectList(
                new LambdaQueryWrapper<PublicationVenueReference>()
                        .eq(PublicationVenueReference::getDatasetId, dataset.getId())
                        .eq(PublicationVenueReference::getVenueType, type.name()));
        List<VenueSearchResult> results = matcher.match(query, candidates, limit).stream().map(match -> {
            PublicationVenueReference ref = match.reference();
            return new VenueSearchResult(ref.getId(), ref.getStandardName(), ref.getAbbreviation(), ref.getVenueType(),
                    ref.getCcfRank(), ref.getCategoryId(), ref.getCategoryName(), dataset.getDataYear(), match.method(),
                    match.confirmationRequired(), dataset.getDatasetKey(), dataset.getDataName());
        }).toList();
        return new VenueSearchResponse(true, results.isEmpty() ? "未找到CCF匹配候选" : "OK", results);
    }

    public List<JournalMetricSuggestion> metricSuggestions(
            String issn, String eissn, String journalName, Integer year, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 20));
        LambdaQueryWrapper<JournalMetricReference> query = new LambdaQueryWrapper<JournalMetricReference>()
                .eq(JournalMetricReference::getActive, true)
                .eq(year != null, JournalMetricReference::getDataYear, year);
        String normalized = PublicationVenueNormalizer.normalize(journalName);
        query.and(wrapper -> {
            boolean any = false;
            if (StringUtils.hasText(issn)) {
                wrapper.eq(JournalMetricReference::getIssn, issn.trim());
                any = true;
            }
            if (StringUtils.hasText(eissn)) {
                if (any) wrapper.or();
                wrapper.eq(JournalMetricReference::getEissn, eissn.trim());
                any = true;
            }
            if (StringUtils.hasText(normalized)) {
                if (any) wrapper.or();
                wrapper.eq(JournalMetricReference::getNormalizedName, normalized);
            }
        });
        if (!StringUtils.hasText(issn) && !StringUtils.hasText(eissn) && !StringUtils.hasText(normalized)) {
            return List.of();
        }
        query.last("LIMIT " + limit);
        return metrics.selectList(query).stream().map(ref -> new JournalMetricSuggestion(
                ref.getId(), ref.getJournalName(), ref.getIssn(), ref.getEissn(), ref.getDataYear(),
                ref.getImpactFactor(), ref.getJcrQuartile(), ref.getSubjectName(), ref.getSourceName(),
                ref.getSourceUrl(), true, "非官方整理，仅供参考，请结合当年度权威数据自行核实"))
                .toList();
    }

    public record VenueSearchResult(Long referenceId, String standardName, String abbreviation, String type,
            String ccfRank, Integer categoryId, String categoryName, Integer dataYear, String matchMethod,
            boolean confirmationRequired, String source, String sourceName) {
    }

    public record VenueSearchResponse(boolean configured, String message, List<VenueSearchResult> items) {}

    public record JournalMetricSuggestion(Long referenceId, String journalName, String issn, String eissn,
            Integer dataYear, java.math.BigDecimal impactFactor, String jcrQuartile, String subject,
            String sourceName, String sourceUrl, boolean confirmationRequired, String warning) {
    }
}
