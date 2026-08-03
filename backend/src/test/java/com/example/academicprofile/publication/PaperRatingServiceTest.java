package com.example.academicprofile.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.academicprofile.achievement.Paper;
import com.example.academicprofile.achievement.PaperRequest;
import com.example.academicprofile.common.exception.BusinessException;

class PaperRatingServiceTest {
    private PublicationVenueReferenceMapper references;
    private PublicationVenueDatasetMapper datasets;
    private JournalMetricReferenceMapper metricReferences;
    private PaperRatingService service;

    @BeforeEach
    void setUp() {
        references = mock(PublicationVenueReferenceMapper.class);
        datasets = mock(PublicationVenueDatasetMapper.class);
        metricReferences = mock(JournalMetricReferenceMapper.class);
        service = new PaperRatingService(references, datasets, metricReferences, new PublicationVenueMatcher());
    }

    @Test
    void conferenceRejectsJournalOnlyFields() {
        Paper paper = new Paper();
        assertThatThrownBy(() -> service.apply(paper, request("CONFERENCE", null, false,
                2026, new BigDecimal("1.2"), "Q1")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void manualJournalMetricIsStoredAsVerifiedManualSnapshot() {
        Paper paper = new Paper();
        service.apply(paper, request("JOURNAL", null, false, 2026, new BigDecimal("3.25"), "Q2"));

        assertThat(paper.getMetricStatus()).isEqualTo("MANUAL");
        assertThat(paper.getMetricConfirmedAt()).isNotNull();
        assertThat(paper.getJournalImpactFactor()).isEqualByComparingTo("3.25");
    }

    @Test
    void confirmedReferenceUsesServerControlledDatasetAndSnapshot() {
        PublicationVenueReference reference = new PublicationVenueReference();
        reference.setId(9L);
        reference.setDatasetId(2L);
        reference.setVenueType("JOURNAL");
        reference.setStandardName("ACM Transactions on Computer Systems");
        reference.setNormalizedName(PublicationVenueNormalizer.normalize(reference.getStandardName()));
        reference.setCcfRank("A");
        reference.setCategoryId(0);
        reference.setCategoryName("计算机体系结构");
        PublicationVenueDataset dataset = new PublicationVenueDataset();
        dataset.setId(2L);
        dataset.setDatasetKey("CCF_CUSTOMER_PROVIDED_2026");
        dataset.setDataYear(2026);
        when(references.selectById(9L)).thenReturn(reference);
        when(datasets.selectById(2L)).thenReturn(dataset);

        Paper paper = new Paper();
        service.apply(paper, request("JOURNAL", 9L, true, null, null, null));

        assertThat(paper.getCcfRank()).isEqualTo("A");
        assertThat(paper.getCcfSource()).isEqualTo("CCF_CUSTOMER_PROVIDED_2026");
        assertThat(paper.getCcfConfirmedAt()).isNotNull();
    }

    @Test
    void nonOfficialMetricReferenceRequiresConfirmationAndUsesServerValues() {
        JournalMetricReference metric = new JournalMetricReference();
        metric.setId(7L);
        metric.setActive(true);
        metric.setJournalName("Synthetic Journal");
        metric.setDataYear(2025);
        metric.setImpactFactor(new BigDecimal("4.2"));
        metric.setJcrQuartile("Q1");
        metric.setSourceName("Licensed synthetic reference");
        metric.setSourceUrl("https://example.test/reference");
        when(metricReferences.selectById(7L)).thenReturn(metric);

        PaperRequest unconfirmed = new PaperRequest("Synthetic paper", null, "Synthetic Journal", "article",
                "JOURNAL", null, null, null, null, false, null, null, null,
                7L, null, null, null, null, null, null, false,
                2026, null, null, null, null, null, null, null, null, 0, false);
        assertThatThrownBy(() -> service.apply(new Paper(), unconfirmed)).isInstanceOf(BusinessException.class);

        PaperRequest confirmed = new PaperRequest("Synthetic paper", null, "Synthetic Journal", "article",
                "JOURNAL", null, null, null, null, false, null, null, null,
                7L, null, null, null, null, null, null, true,
                2026, null, null, null, null, null, null, null, null, 0, false);
        Paper paper = new Paper();
        service.apply(paper, confirmed);
        assertThat(paper.getMetricStatus()).isEqualTo("USER_CONFIRMED");
        assertThat(paper.getJournalImpactFactor()).isEqualByComparingTo("4.2");
    }

    private PaperRequest request(String venueType, Long referenceId, boolean confirmed,
            Integer metricYear, BigDecimal impactFactor, String quartile) {
        return new PaperRequest("Synthetic paper", null, "ACM Transactions on Computer Systems", "article",
                venueType, null, null, null, referenceId, confirmed, null, null, null,
                null, metricYear, impactFactor, quartile, null, "User verified source", null, false,
                2026, null, null, null, null, null, null, null, null, 0, false);
    }
}
