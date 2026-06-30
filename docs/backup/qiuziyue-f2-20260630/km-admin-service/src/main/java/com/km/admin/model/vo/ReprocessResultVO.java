package com.km.admin.model.vo;

public class ReprocessResultVO {

    private Long kbId;
    private Integer readyDocumentCount;
    private String message;
    private KnowledgeBaseSnapshotVO snapshot;

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public Integer getReadyDocumentCount() { return readyDocumentCount; }
    public void setReadyDocumentCount(Integer readyDocumentCount) { this.readyDocumentCount = readyDocumentCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public KnowledgeBaseSnapshotVO getSnapshot() { return snapshot; }
    public void setSnapshot(KnowledgeBaseSnapshotVO snapshot) { this.snapshot = snapshot; }
}
