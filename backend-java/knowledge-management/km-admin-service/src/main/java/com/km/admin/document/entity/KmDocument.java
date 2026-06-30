package com.km.admin.document.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档实体。R12：字段名按 SQL 真实列名做别名映射（file_name -> originalName 等）。
 * R14：uploaderUserId 是 String UUID。
 */
public class KmDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long kbId;
    /** 来自 file_name 列 */
    private String originalName;
    /** 来自 object_key 列 */
    private String filePath;
    private String mimeType;
    private Long fileSize;
    private String fileHash;
    private String extension;
    /** 来自 document_status 列 */
    private String status;
    private String errorStage;
    private String errorMessage;
    private Integer chunkCount;
    private Integer retryCount;
    /** 来自 user_id 列，R14：String UUID */
    private String uploaderUserId;
    private String uploaderName;
    private Long currentVersionNo;
    private Long nextVersionNo;
    private Integer isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorStage() { return errorStage; }
    public void setErrorStage(String errorStage) { this.errorStage = errorStage; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getUploaderUserId() { return uploaderUserId; }
    public void setUploaderUserId(String uploaderUserId) { this.uploaderUserId = uploaderUserId; }

    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }

    public Long getCurrentVersionNo() { return currentVersionNo; }
    public void setCurrentVersionNo(Long currentVersionNo) { this.currentVersionNo = currentVersionNo; }

    public Long getNextVersionNo() { return nextVersionNo; }
    public void setNextVersionNo(Long nextVersionNo) { this.nextVersionNo = nextVersionNo; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
