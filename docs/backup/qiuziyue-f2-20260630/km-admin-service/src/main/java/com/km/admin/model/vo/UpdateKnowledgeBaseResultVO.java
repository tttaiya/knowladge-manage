package com.km.admin.model.vo;

public class UpdateKnowledgeBaseResultVO {

    private Long id;
    private Boolean strategyChanged;
    private Boolean reprocessTriggered;
    private Integer readyDocumentCount;
    private KnowledgeBaseSnapshotVO snapshot;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Boolean getStrategyChanged() { return strategyChanged; }
    public void setStrategyChanged(Boolean strategyChanged) { this.strategyChanged = strategyChanged; }
    public Boolean getReprocessTriggered() { return reprocessTriggered; }
    public void setReprocessTriggered(Boolean reprocessTriggered) { this.reprocessTriggered = reprocessTriggered; }
    public Integer getReadyDocumentCount() { return readyDocumentCount; }
    public void setReadyDocumentCount(Integer readyDocumentCount) { this.readyDocumentCount = readyDocumentCount; }
    public KnowledgeBaseSnapshotVO getSnapshot() { return snapshot; }
    public void setSnapshot(KnowledgeBaseSnapshotVO snapshot) { this.snapshot = snapshot; }
}
