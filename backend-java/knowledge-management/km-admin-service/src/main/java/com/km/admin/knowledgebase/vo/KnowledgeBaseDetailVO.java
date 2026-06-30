package com.km.admin.knowledgebase.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库详情 VO。
 * F2 v1.0 5.4 节：详情包含 separatorsJson 解析后的 List 视图。
 */
public class KnowledgeBaseDetailVO {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String retrievalStrategy;
    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;

    /** 解析后的分隔符列表（避免前端再做 JSON.parse） */
    private List<String> separators;

    /** 原始 JSON 字符串，调试/审计用 */
    private String separatorsJson;

    private Integer documentCount;
    private String createdByUserId;
    private String createdByName;
    private Long strategyVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

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

    public String getSeparatorsJson() { return separatorsJson; }
    public void setSeparatorsJson(String separatorsJson) { this.separatorsJson = separatorsJson; }

    public Integer getDocumentCount() { return documentCount; }
    public void setDocumentCount(Integer documentCount) { this.documentCount = documentCount; }

    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public Long getStrategyVersion() { return strategyVersion; }
    public void setStrategyVersion(Long strategyVersion) { this.strategyVersion = strategyVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
