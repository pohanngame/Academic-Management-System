package com.example.academicprofile.file;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("file_metadata")
public class FileMetadata {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String originalName;
    private String storedName;
    private String storagePath;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String businessType;
    private Long businessId;
    private String encryptionVersion;
    private String encryptionKeyId;
    private String wrappedDek;
    private String ciphertextStatus;
    private String plaintextSha256;
    private String legacyStoragePath;
    private Boolean deleted;
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

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getFileExt() {
        return fileExt;
    }

    public void setFileExt(String fileExt) {
        this.fileExt = fileExt;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public String getEncryptionVersion() { return encryptionVersion; }
    public void setEncryptionVersion(String encryptionVersion) { this.encryptionVersion = encryptionVersion; }
    public String getEncryptionKeyId() { return encryptionKeyId; }
    public void setEncryptionKeyId(String encryptionKeyId) { this.encryptionKeyId = encryptionKeyId; }
    public String getWrappedDek() { return wrappedDek; }
    public void setWrappedDek(String wrappedDek) { this.wrappedDek = wrappedDek; }
    public String getCiphertextStatus() { return ciphertextStatus; }
    public void setCiphertextStatus(String ciphertextStatus) { this.ciphertextStatus = ciphertextStatus; }
    public String getPlaintextSha256() { return plaintextSha256; }
    public void setPlaintextSha256(String plaintextSha256) { this.plaintextSha256 = plaintextSha256; }
    public String getLegacyStoragePath() { return legacyStoragePath; }
    public void setLegacyStoragePath(String legacyStoragePath) { this.legacyStoragePath = legacyStoragePath; }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
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
