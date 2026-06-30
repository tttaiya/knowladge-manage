package com.km.admin.review.vo;

import java.time.LocalDateTime;

/**
 * 待审核文档 VO。已去除 Lombok。
 */
public class PendingReviewDocumentVO {

    private Long docId;
    private Long kbId;
    private String kbName;
    private String originalName;
    private String status;
    private Integer chunkCount;
    private String uploaderName;
    private LocalDateTime createdAt;

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }

    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }

    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
