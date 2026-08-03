package com.example.academicprofile.publication;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.academicprofile.admin.AdminAuditService;

@Service
public class CcfReferenceImporter {
    public static final String AUDIT_ACTION = "CCF_REFERENCE_IMPORT";

    private final PublicationVenueDatasetMapper datasets;
    private final PublicationVenueReferenceMapper references;
    private final CcfReferenceSpecification specification;
    private final AdminAuditService audit;

    public CcfReferenceImporter(PublicationVenueDatasetMapper datasets,
            PublicationVenueReferenceMapper references,
            CcfReferenceSpecification specification,
            AdminAuditService audit) {
        this.datasets = datasets;
        this.references = references;
        this.specification = specification;
        this.audit = audit;
    }

    @Transactional
    public ImportResult importValidated(CcfReferenceUploadParser.ParsedDataset parsed, String sha256, Long operatorId) {
        List<PublicationVenueDataset> existingDatasets = datasets.selectList(null);
        if (existingDatasets.size() > 1) {
            throw new IllegalStateException("CCF reference dataset state is inconsistent");
        }
        PublicationVenueDataset dataset = existingDatasets.isEmpty() ? null : existingDatasets.get(0);
        if (dataset != null && sha256.equals(dataset.getSourceSha256())) {
            long count = references.selectCount(new LambdaQueryWrapper<PublicationVenueReference>()
                    .eq(PublicationVenueReference::getDatasetId, dataset.getId()));
            if (count != parsed.recordCount() || count != dataset.getRecordCount()) {
                throw new IllegalStateException("CCF reference dataset is incomplete");
            }
            recordAudit(operatorId, parsed, sha256, true);
            return result(false, dataset, parsed, sha256);
        }

        if (dataset == null) {
            dataset = new PublicationVenueDataset();
            applyDataset(dataset, parsed, sha256);
            datasets.insert(dataset);
        } else {
            references.delete(new LambdaQueryWrapper<PublicationVenueReference>()
                    .eq(PublicationVenueReference::getDatasetId, dataset.getId()));
            applyDataset(dataset, parsed, sha256);
            datasets.updateById(dataset);
        }
        for (CcfReferenceUploadParser.ParsedReference parsedReference : parsed.references()) {
            references.insert(toEntity(dataset.getId(), parsedReference));
        }
        recordAudit(operatorId, parsed, sha256, false);
        return result(true, dataset, parsed, sha256);
    }

    private void applyDataset(PublicationVenueDataset dataset,
            CcfReferenceUploadParser.ParsedDataset parsed, String sha256) {
        dataset.setDatasetKey(specification.datasetKey());
        dataset.setDataName(parsed.name());
        dataset.setDataYear(parsed.year());
        dataset.setSourceKind(specification.sourceKind());
        dataset.setSourcePage(specification.sourcePage());
        dataset.setSourceSha256(sha256);
        dataset.setRecordCount(parsed.recordCount());
        dataset.setCreatedAt(LocalDateTime.now());
    }

    private PublicationVenueReference toEntity(Long datasetId,
            CcfReferenceUploadParser.ParsedReference parsed) {
        PublicationVenueReference reference = new PublicationVenueReference();
        reference.setDatasetId(datasetId);
        reference.setVenueType(parsed.venueType().name());
        reference.setCategoryId(parsed.categoryId());
        reference.setCategoryName(parsed.categoryName());
        reference.setCategoryNameEn(parsed.categoryNameEn());
        reference.setCcfRank(parsed.rank());
        reference.setAbbreviation(parsed.abbreviation());
        reference.setStandardName(parsed.standardName());
        reference.setNormalizedName(parsed.normalizedName());
        reference.setNormalizedAbbreviation(parsed.normalizedAbbreviation());
        reference.setAliases(parsed.aliases());
        reference.setPublisher(parsed.publisher());
        reference.setSourceUrl(parsed.sourceUrl());
        return reference;
    }

    private void recordAudit(Long operatorId, CcfReferenceUploadParser.ParsedDataset parsed,
            String sha256, boolean idempotent) {
        audit.record(operatorId, AUDIT_ACTION, null, true,
                "CCF year=" + parsed.year()
                        + ", categories=" + parsed.categoryCount()
                        + ", journals=" + parsed.journalCount()
                        + ", conferences=" + parsed.conferenceCount()
                        + ", sha256=" + sha256
                        + (idempotent ? ", idempotent=true" : ""));
    }

    private ImportResult result(boolean imported, PublicationVenueDataset dataset,
            CcfReferenceUploadParser.ParsedDataset parsed, String sha256) {
        return new ImportResult(imported, dataset.getId(), parsed.name(), parsed.year(), sha256,
                parsed.categoryCount(), parsed.journalCount(), parsed.conferenceCount(), dataset.getCreatedAt());
    }

    public record ImportResult(boolean imported, Long datasetId, String dataName, int dataYear,
            String sha256, int categoryCount, int journalCount, int conferenceCount,
            LocalDateTime importedAt) {}
}
