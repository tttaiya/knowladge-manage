package com.km.admin.model.vo;

public class KnowledgeBaseSnapshotVO {

    private Long kbId;
    private String kbName;
    private String retrievalStrategy;
    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String separatorsJson;
    private Long operatorUserId;
    private String operatorName;
    private String triggerReason;

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }
    public String getRetrievalStrategy() { return retrievalStrategy; }
    public void setRetrievalStrategy(String retrievalStrategy) { this.retrievalStrategy = retrievalStrategy; }
    public String getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(String chunkStrategy) { this.chunkStrategy = chunkStrategy; }
    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }
    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public String getSeparatorsJson() { return separatorsJson; }
    public void setSeparatorsJson(String separatorsJson) { this.separatorsJson = separatorsJson; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }
}
