package com.example.academicprofile.publication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("journal_metric_reference")
public class JournalMetricReference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String journalName;
    private String normalizedName;
    private String issn;
    private String eissn;
    private Integer dataYear;
    private BigDecimal impactFactor;
    private String jcrQuartile;
    private String subjectName;
    private String sourceName;
    private String sourceUrl;
    private String sourceLicense;
    private String sourceSha256;
    private Boolean active;
    private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getJournalName(){return journalName;} public void setJournalName(String v){journalName=v;}
    public String getNormalizedName(){return normalizedName;} public void setNormalizedName(String v){normalizedName=v;}
    public String getIssn(){return issn;} public void setIssn(String v){issn=v;}
    public String getEissn(){return eissn;} public void setEissn(String v){eissn=v;}
    public Integer getDataYear(){return dataYear;} public void setDataYear(Integer v){dataYear=v;}
    public BigDecimal getImpactFactor(){return impactFactor;} public void setImpactFactor(BigDecimal v){impactFactor=v;}
    public String getJcrQuartile(){return jcrQuartile;} public void setJcrQuartile(String v){jcrQuartile=v;}
    public String getSubjectName(){return subjectName;} public void setSubjectName(String v){subjectName=v;}
    public String getSourceName(){return sourceName;} public void setSourceName(String v){sourceName=v;}
    public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
    public String getSourceLicense(){return sourceLicense;} public void setSourceLicense(String v){sourceLicense=v;}
    public String getSourceSha256(){return sourceSha256;} public void setSourceSha256(String v){sourceSha256=v;}
    public Boolean getActive(){return active;} public void setActive(Boolean v){active=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
