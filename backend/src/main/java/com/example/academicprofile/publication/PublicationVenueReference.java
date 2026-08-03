package com.example.academicprofile.publication;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("publication_venue_reference")
public class PublicationVenueReference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String venueType;
    private Integer categoryId;
    private String categoryName;
    private String categoryNameEn;
    private String ccfRank;
    private String abbreviation;
    private String standardName;
    private String normalizedName;
    private String normalizedAbbreviation;
    private String aliases;
    private String publisher;
    private String sourceUrl;
    private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getDatasetId(){return datasetId;} public void setDatasetId(Long v){datasetId=v;}
    public String getVenueType(){return venueType;} public void setVenueType(String v){venueType=v;}
    public Integer getCategoryId(){return categoryId;} public void setCategoryId(Integer v){categoryId=v;}
    public String getCategoryName(){return categoryName;} public void setCategoryName(String v){categoryName=v;}
    public String getCategoryNameEn(){return categoryNameEn;} public void setCategoryNameEn(String v){categoryNameEn=v;}
    public String getCcfRank(){return ccfRank;} public void setCcfRank(String v){ccfRank=v;}
    public String getAbbreviation(){return abbreviation;} public void setAbbreviation(String v){abbreviation=v;}
    public String getStandardName(){return standardName;} public void setStandardName(String v){standardName=v;}
    public String getNormalizedName(){return normalizedName;} public void setNormalizedName(String v){normalizedName=v;}
    public String getNormalizedAbbreviation(){return normalizedAbbreviation;} public void setNormalizedAbbreviation(String v){normalizedAbbreviation=v;}
    public String getAliases(){return aliases;} public void setAliases(String v){aliases=v;}
    public String getPublisher(){return publisher;} public void setPublisher(String v){publisher=v;}
    public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
