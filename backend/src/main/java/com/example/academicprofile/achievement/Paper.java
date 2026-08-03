package com.example.academicprofile.achievement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("paper")
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String title;
    private String authors;
    private String publicationName;
    private String publicationType;
    private String venueType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String venueAbbreviation;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String issn;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String eissn;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ccfReferenceId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ccfRank;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer ccfCategoryId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ccfCategoryName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer ccfDataYear;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ccfSource;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ccfMatchMethod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime ccfConfirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer jcrDataYear;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal journalImpactFactor;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String jcrQuartile;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String jcrSubject;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String metricSourceDescription;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String metricSourceUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String metricStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime metricConfirmedAt;
    private Integer publishYear;
    private String doi;
    private String volume;
    private String issue;
    private String pages;
    private String publisher;
    private String url;
    private String abstractText;
    private String keywords;
    private Integer sortOrder;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getPublicationName() {
        return publicationName;
    }

    public void setPublicationName(String publicationName) {
        this.publicationName = publicationName;
    }

    public String getPublicationType() {
        return publicationType;
    }

    public void setPublicationType(String publicationType) {
        this.publicationType = publicationType;
    }

    public String getVenueType() { return venueType; }
    public void setVenueType(String venueType) { this.venueType = venueType; }
    public String getVenueAbbreviation() { return venueAbbreviation; }
    public void setVenueAbbreviation(String venueAbbreviation) { this.venueAbbreviation = venueAbbreviation; }
    public String getIssn() { return issn; }
    public void setIssn(String issn) { this.issn = issn; }
    public String getEissn() { return eissn; }
    public void setEissn(String eissn) { this.eissn = eissn; }
    public Long getCcfReferenceId() { return ccfReferenceId; }
    public void setCcfReferenceId(Long ccfReferenceId) { this.ccfReferenceId = ccfReferenceId; }
    public String getCcfRank() { return ccfRank; }
    public void setCcfRank(String ccfRank) { this.ccfRank = ccfRank; }
    public Integer getCcfCategoryId() { return ccfCategoryId; }
    public void setCcfCategoryId(Integer ccfCategoryId) { this.ccfCategoryId = ccfCategoryId; }
    public String getCcfCategoryName() { return ccfCategoryName; }
    public void setCcfCategoryName(String ccfCategoryName) { this.ccfCategoryName = ccfCategoryName; }
    public Integer getCcfDataYear() { return ccfDataYear; }
    public void setCcfDataYear(Integer ccfDataYear) { this.ccfDataYear = ccfDataYear; }
    public String getCcfSource() { return ccfSource; }
    public void setCcfSource(String ccfSource) { this.ccfSource = ccfSource; }
    public String getCcfMatchMethod() { return ccfMatchMethod; }
    public void setCcfMatchMethod(String ccfMatchMethod) { this.ccfMatchMethod = ccfMatchMethod; }
    public LocalDateTime getCcfConfirmedAt() { return ccfConfirmedAt; }
    public void setCcfConfirmedAt(LocalDateTime ccfConfirmedAt) { this.ccfConfirmedAt = ccfConfirmedAt; }
    public Integer getJcrDataYear() { return jcrDataYear; }
    public void setJcrDataYear(Integer jcrDataYear) { this.jcrDataYear = jcrDataYear; }
    public BigDecimal getJournalImpactFactor() { return journalImpactFactor; }
    public void setJournalImpactFactor(BigDecimal journalImpactFactor) { this.journalImpactFactor = journalImpactFactor; }
    public String getJcrQuartile() { return jcrQuartile; }
    public void setJcrQuartile(String jcrQuartile) { this.jcrQuartile = jcrQuartile; }
    public String getJcrSubject() { return jcrSubject; }
    public void setJcrSubject(String jcrSubject) { this.jcrSubject = jcrSubject; }
    public String getMetricSourceDescription() { return metricSourceDescription; }
    public void setMetricSourceDescription(String metricSourceDescription) { this.metricSourceDescription = metricSourceDescription; }
    public String getMetricSourceUrl() { return metricSourceUrl; }
    public void setMetricSourceUrl(String metricSourceUrl) { this.metricSourceUrl = metricSourceUrl; }
    public String getMetricStatus() { return metricStatus; }
    public void setMetricStatus(String metricStatus) { this.metricStatus = metricStatus; }
    public LocalDateTime getMetricConfirmedAt() { return metricConfirmedAt; }
    public void setMetricConfirmedAt(LocalDateTime metricConfirmedAt) { this.metricConfirmedAt = metricConfirmedAt; }

    public Integer getPublishYear() {
        return publishYear;
    }

    public void setPublishYear(Integer publishYear) {
        this.publishYear = publishYear;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
