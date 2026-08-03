package com.example.academicprofile.access;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("resource_access_grant")
public class ResourceAccessGrant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long requesterTeacherId;
    private Long ownerTeacherId;
    private String permissionType;
    private String resourceType;
    private Long resourceId;
    private String purpose;
    private LocalDateTime effectiveAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long revokedByTeacherId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getRequesterTeacherId() { return requesterTeacherId; }
    public void setRequesterTeacherId(Long requesterTeacherId) { this.requesterTeacherId = requesterTeacherId; }
    public Long getOwnerTeacherId() { return ownerTeacherId; }
    public void setOwnerTeacherId(Long ownerTeacherId) { this.ownerTeacherId = ownerTeacherId; }
    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public LocalDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(LocalDateTime effectiveAt) { this.effectiveAt = effectiveAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public Long getRevokedByTeacherId() { return revokedByTeacherId; }
    public void setRevokedByTeacherId(Long revokedByTeacherId) { this.revokedByTeacherId = revokedByTeacherId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
