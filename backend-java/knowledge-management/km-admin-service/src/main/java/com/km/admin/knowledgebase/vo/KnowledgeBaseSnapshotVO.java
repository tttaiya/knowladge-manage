package com.km.admin.knowledgebase.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库策略快照 VO。
 * F2 v1.0 5.5 节：策略变更时需保存完整快照到 task_payload_json。
 * commit #29 由 KnowledgeBaseTaskFacade 在创建 REPROCESS 任务时序列化。
 */
public class KnowledgeBaseSnapshotVO {

    private Long id;
    private String name;
    private String retrievalStrategy;
    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private List<String> separators;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime capturedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRetrievalStrategy() { return retrievalStrategy; }
    public void setRetrievalStrategy(String retrievalStrategy) { this.retrievalStrategy = retrievalStrategy; }

    public String getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(String chunkStrategy) { this.chunkStrategy = chunkStrategy; }

    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }

    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public List<String> getSeparators() { return separators; }
    public void setSeparators(List<String> separators) { this.separators = separators; }

    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
}
