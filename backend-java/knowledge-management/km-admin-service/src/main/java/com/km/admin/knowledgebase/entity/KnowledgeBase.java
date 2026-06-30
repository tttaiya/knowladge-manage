package com.km.admin.knowledgebase.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库实体。
 * F2 v1.0 5.1 节字段表。
 *
 * <p>字段映射（V5 加法迁移后）：
 * <ul>
 *   <li>id                       → id (PK)
 *   <li>name                     → name
 *   <li>description              → description (F2 新增)
 *   <li>category                 → category (F2 新增)
 *   <li>retrievalStrategy        → retrieval_strategy
 *   <li>chunkStrategy            → chunk_strategy (F2 新增)
 *   <li>chunkSize                → chunk_size (F2 新增)
 *   <li>chunkOverlap             → chunk_overlap (F2 新增)
 *   <li>separatorsJson           → separators_json (F2 新增)
 *   <li>documentCount            → document_count (F2 新增；触发器维护)
 *   <li>createdByUserId          → created_by_user_id (F2 新增；VARCHAR(36) UUID)
 *   <li>createdByName            → created_by_name (F2 新增)
 *   <li>strategyVersion          → strategy_version (F2 新增)
 *   <li>isDeleted                → is_deleted
 *   <li>deletedAt                → deleted_at (F2 新增)
 *   <li>createdAt                → created_at
 *   <li>updatedAt                → updated_at
 *   <li>activeName               → active_name (GENERATED STORED 列，Mapper 不参与 SELECT)
 * </ul>
 */
public class KnowledgeBase implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String category;
    private String retrievalStrategy;
    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String separatorsJson;
    private Integer documentCount;
    private String createdByUserId;
    private String createdByName;
    private Long strategyVersion;
    private Integer isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
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

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** activeName 是 GENERATED STORED 列，Mapper 不查询；提供 getter 以备调试。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonIgnore
    public String getActiveName() {
        return Integer.valueOf(0).equals(isDeleted) ? name : null;
    }
}
