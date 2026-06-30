package com.km.admin.task.dto;

/**
 * 创建 PROCESS 任务请求。
 * 必改 2.4：原 Map<String, Long> 改为 DTO，userId 是 String UUID。
 */
public class CreateProcessTaskRequest {
    private Long docId;
    private String userId;

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
