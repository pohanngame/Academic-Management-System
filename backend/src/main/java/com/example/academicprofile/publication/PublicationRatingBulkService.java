package com.example.academicprofile.publication;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperMapper;
import com.example.academicprofile.common.exception.BusinessException;

@Service
public class PublicationRatingBulkService {
    private final PaperMapper papers;
    private final PublicationVenueReferenceMapper references;
    private final PublicationVenueMatcher matcher;
    private final PaperRatingService ratings;

    public PublicationRatingBulkService(PaperMapper papers, PublicationVenueReferenceMapper references,
            PublicationVenueMatcher matcher, PaperRatingService ratings) {
        this.papers = papers;
        this.references = references;
        this.matcher = matcher;
        this.ratings = ratings;
    }

    public PreviewPage preview(Long teacherId, int pageNumber, int requestedSize) {
        int size = Math.max(1, Math.min(requestedSize, 100));
        int page = Math.max(1, pageNumber);
        Page<Paper> result = papers.selectPage(new Page<>(page, size), new LambdaQueryWrapper<Paper>()
                .eq(Paper::getTeacherId, teacherId)
                .orderByDesc(Paper::getId));
        List<PublicationVenueReference> all = references.selectList(null);
        List<PreviewItem> items = new ArrayList<>();
        for (Paper paper : result.getRecords()) {
            VenueType type = parseType(paper.getVenueType());
            if (paper.getCcfConfirmedAt() != null) {
                items.add(new PreviewItem(paper.getId(), paper.getTitle(), paper.getPublicationName(), type.name(),
                        "ALREADY_CONFIRMED", List.of()));
                continue;
            }
            if (type == VenueType.UNKNOWN || paper.getPublicationName() == null) {
                items.add(new PreviewItem(paper.getId(), paper.getTitle(), paper.getPublicationName(), type.name(),
                        "UNMATCHED", List.of()));
                continue;
            }
            List<PublicationVenueMatcher.Match> matches = matcher.match(paper.getPublicationName(), all.stream()
                    .filter(ref -> type.name().equals(ref.getVenueType())).toList(), 5);
            String status = matches.isEmpty() ? "UNMATCHED"
                    : matches.stream().anyMatch(match -> !"FUZZY_CANDIDATE".equals(match.method()))
                            ? "EXACT" : "FUZZY";
            items.add(new PreviewItem(paper.getId(), paper.getTitle(), paper.getPublicationName(), type.name(), status,
                    matches.stream().map(match -> new Candidate(match.reference().getId(),
                            match.reference().getStandardName(), match.reference().getAbbreviation(),
                            match.reference().getCcfRank(), match.reference().getCategoryName(), match.method(),
                            match.confirmationRequired())).toList()));
        }
        return new PreviewPage(result.getTotal(), page, size, items);
    }

    @Transactional
    public int confirm(Long teacherId, BulkConfirmRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty() || request.items().size() > 100) {
            throw new BusinessException("批量确认数量必须为 1 至 100 条");
        }
        if (request.items().stream().anyMatch(item -> item == null || item.paperId() == null || item.referenceId() == null)
                || request.items().stream().map(BulkConfirmItem::paperId).distinct().count() != request.items().size()) {
            throw new BusinessException("批量确认记录无效或重复");
        }
        int updated = 0;
        for (BulkConfirmItem item : request.items()) {
            Paper paper = papers.selectById(item.paperId());
            if (paper == null || !teacherId.equals(paper.getTeacherId())) {
                throw new BusinessException("论文不存在或无权修改");
            }
            if (paper.getCcfConfirmedAt() != null && !request.replaceExisting()) continue;
            VenueType type = parseType(paper.getVenueType());
            ratings.confirmReference(paper, item.referenceId(), type, paper.getPublicationName());
            papers.updateById(paper);
            updated++;
        }
        return updated;
    }

    private VenueType parseType(String value) {
        try {
            return value == null ? VenueType.UNKNOWN : VenueType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return VenueType.UNKNOWN;
        }
    }

    public record PreviewPage(long total, int page, int size, List<PreviewItem> items) { }
    public record PreviewItem(Long paperId, String title, String venueName, String venueType, String status,
            List<Candidate> candidates) { }
    public record Candidate(Long referenceId, String standardName, String abbreviation, String ccfRank,
            String categoryName, String matchMethod, boolean confirmationRequired) { }
    public record BulkConfirmRequest(List<BulkConfirmItem> items, boolean replaceExisting) { }
    public record BulkConfirmItem(Long paperId, Long referenceId) { }
}
