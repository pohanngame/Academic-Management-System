package com.example.academicprofile.access;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("resource_access_request")
public class ResourceAccessRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterTeacherId;
    private Long ownerTeacherId;
    private String resourceType;
    private String permissionType;
    private String purpose;
    private String status;
    private LocalDateTime requestedExpiresAt;
    private LocalDateTime decidedAt;
    private String decisionComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRequesterTeacherId() { return requesterTeacherId; }
    public void setRequesterTeacherId(Long requesterTeacherId) { this.requesterTeacherId = requesterTeacherId; }
    public Long getOwnerTeacherId() { return ownerTeacherId; }
    public void setOwnerTeacherId(Long ownerTeacherId) { this.ownerTeacherId = ownerTeacherId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRequestedExpiresAt() { return requestedExpiresAt; }
    public void setRequestedExpiresAt(LocalDateTime requestedExpiresAt) { this.requestedExpiresAt = requestedExpiresAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
