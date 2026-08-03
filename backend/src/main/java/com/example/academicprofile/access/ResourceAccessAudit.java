package com.example.academicprofile.access;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("resource_access_audit")
public class ResourceAccessAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorUserId;
    private Long actorTeacherId;
    private Long ownerTeacherId;
    private String operationType;
    private String resourceType;
    private Long resourceId;
    private Long fileId;
    private Long requestId;
    private Long grantId;
    private String purpose;
    private Boolean success;
    private String ipAddress;
    private String clientInfo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public Long getActorTeacherId() { return actorTeacherId; }
    public void setActorTeacherId(Long actorTeacherId) { this.actorTeacherId = actorTeacherId; }
    public Long getOwnerTeacherId() { return ownerTeacherId; }
    public void setOwnerTeacherId(Long ownerTeacherId) { this.ownerTeacherId = ownerTeacherId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getGrantId() { return grantId; }
    public void setGrantId(Long grantId) { this.grantId = grantId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getClientInfo() { return clientInfo; }
    public void setClientInfo(String clientInfo) { this.clientInfo = clientInfo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
