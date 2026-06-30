package com.km.admin.knowledgebase.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * 策略变更（reprocess）结果 VO。
 * F2 v1.0 5.4 节：commit #29 启用 KnowledgeBaseTaskFacade 后，
 * 真实 taskId 由此 VO 返回，前端可跳转 /task-center/?taskId=xxx。
 */
public class ReprocessKnowledgeBaseResultVO {

    private Long knowledgeBaseId;
    private Long taskId;
    private Integer readyDocumentCount;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime triggeredAt;

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Integer getReadyDocumentCount() { return readyDocumentCount; }
    public void setReadyDocumentCount(Integer readyDocumentCount) { this.readyDocumentCount = readyDocumentCount; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
}
