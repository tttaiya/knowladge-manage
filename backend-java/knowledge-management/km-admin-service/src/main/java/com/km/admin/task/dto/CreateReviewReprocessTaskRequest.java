package com.km.admin.task.dto;

/**
 * 创建审核拒绝后 REPROCESS 任务请求。
 * 必改 2.4：原 Map<String, Long> 改为 DTO，operatorUserId 是 String UUID。
 */
public class CreateReviewReprocessTaskRequest {
    private Long docId;
    private Long sourceReviewId;
    private String operatorUserId;

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }

    public Long getSourceReviewId() { return sourceReviewId; }
    public void setSourceReviewId(Long sourceReviewId) { this.sourceReviewId = sourceReviewId; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }
}
