package com.km.admin.task.dto;

/**
 * 创建 REEMBED 任务请求。
 * 必改 2.4：原 Map<String, Long> 改为 DTO，operatorUserId 是 String UUID。
 */
public class CreateReembedTaskRequest {
    private Long docId;
    private Long chunkId;
    private String operatorUserId;
    private Long chunkContentVersion;

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }

    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }

    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }

    public Long getChunkContentVersion() { return chunkContentVersion; }
    public void setChunkContentVersion(Long chunkContentVersion) { this.chunkContentVersion = chunkContentVersion; }
}
