package com.km.admin.review.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 切片编辑日志。R14：operatorUserId 改 String UUID；已去除 Lombok。
 */
public class ChunkEditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long chunkId;
    private String beforeContent;
    private String afterContent;
    private String action;
    private String operatorUserId;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }

    public String getBeforeContent() { return beforeContent; }
    public void setBeforeContent(String beforeContent) { this.beforeContent = beforeContent; }

    public String getAfterContent() { return afterContent; }
    public void setAfterContent(String afterContent) { this.afterContent = afterContent; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
