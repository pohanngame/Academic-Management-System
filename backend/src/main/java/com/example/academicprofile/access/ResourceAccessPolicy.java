package com.example.academicprofile.access;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("resource_access_policy")
public class ResourceAccessPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerTeacherId;
    private String resourceType;
    private Long resourceId;
    private Boolean viewAllowed;
    private Boolean downloadAllowed;
    private Boolean aiUseAllowed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerTeacherId() { return ownerTeacherId; }
    public void setOwnerTeacherId(Long ownerTeacherId) { this.ownerTeacherId = ownerTeacherId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public Boolean getViewAllowed() { return viewAllowed; }
    public void setViewAllowed(Boolean viewAllowed) { this.viewAllowed = viewAllowed; }
    public Boolean getDownloadAllowed() { return downloadAllowed; }
    public void setDownloadAllowed(Boolean downloadAllowed) { this.downloadAllowed = downloadAllowed; }
    public Boolean getAiUseAllowed() { return aiUseAllowed; }
    public void setAiUseAllowed(Boolean aiUseAllowed) { this.aiUseAllowed = aiUseAllowed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
