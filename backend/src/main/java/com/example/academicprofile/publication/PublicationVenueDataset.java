package com.example.academicprofile.publication;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("publication_venue_dataset")
public class PublicationVenueDataset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String datasetKey;
    private String dataName;
    private Integer dataYear;
    private String sourceKind;
    private String sourcePage;
    private String sourceSha256;
    private Integer recordCount;
    private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getDatasetKey(){return datasetKey;} public void setDatasetKey(String v){datasetKey=v;}
    public String getDataName(){return dataName;} public void setDataName(String v){dataName=v;}
    public Integer getDataYear(){return dataYear;} public void setDataYear(Integer v){dataYear=v;}
    public String getSourceKind(){return sourceKind;} public void setSourceKind(String v){sourceKind=v;}
    public String getSourcePage(){return sourcePage;} public void setSourcePage(String v){sourcePage=v;}
    public String getSourceSha256(){return sourceSha256;} public void setSourceSha256(String v){sourceSha256=v;}
    public Integer getRecordCount(){return recordCount;} public void setRecordCount(Integer v){recordCount=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
