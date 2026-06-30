package com.km.admin.review.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人工审核记录。R14：operatorUserId 改 String UUID；已去除 Lombok。
 */
public class ReviewRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long docId;
    private String action;
    private String comment;
    private String operatorUserId;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
