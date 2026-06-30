package com.km.admin.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class KnowledgeBaseCreateDTO {

    @NotBlank(message = "?????????")
    private String name;
    private String description;
    @NotBlank(message = "?????????")
    private String category;
    @NotBlank(message = "????????")
    private String retrievalStrategy;
    @NotBlank(message = "????????")
    private String chunkStrategy;
    @NotNull(message = "chunkSize ????")
    private Integer chunkSize;
    @NotNull(message = "chunkOverlap ????")
    private Integer chunkOverlap;
    private String separatorsJson;

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
}
