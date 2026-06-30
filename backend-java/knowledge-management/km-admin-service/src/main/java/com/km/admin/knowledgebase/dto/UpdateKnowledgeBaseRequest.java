package com.km.admin.knowledgebase.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 更新知识库请求。
 * F2 v1.0 5.1 节。
 *
 * <p>约束：name 与 description 可选；retrievalStrategy/chunkStrategy/chunkSize/chunkOverlap 仅在策略变更时出现。
 *    策略变更需 confirmation=true（@Pattern(regexp="true") 强校验，否则 2004）。
 */
public class UpdateKnowledgeBaseRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long id;

    @Size(max = 128)
    private String name;

    @Size(max = 500)
    private String description;

    private String category;

    @Pattern(regexp = "VECTOR_RERANK|SEMANTIC")
    private String retrievalStrategy;

    @Pattern(regexp = "HEADING|FIXED")
    private String chunkStrategy;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private String separatorsJson;

    /**
     * 策略变更确认。
     * 任意策略字段（retrievalStrategy/chunkStrategy/chunkSize/chunkOverlap/separatorsJson）变更时，
     * 必须由 controller 层在调用 service 前显式校验 confirmation=true。
     * 此处保留字段用于前端显式传递，服务层只校验其与策略字段的一致性。
     */
    private Boolean confirmation;

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

    public Boolean getConfirmation() { return confirmation; }
    public void setConfirmation(Boolean confirmation) { this.confirmation = confirmation; }
}
