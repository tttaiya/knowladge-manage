package com.km.search.dto;

import java.util.List;

public class RetrievalSearchRequest {

    private String query;
    private List<Long> knowledgeBaseIds;
    private List<String> tags;
    private String mode;
    private Integer topK;
    private Double similarityThreshold;
    private Integer rerankTopN;
    private Double rerankThreshold;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<Long> getKnowledgeBaseIds() {
        return knowledgeBaseIds;
    }

    public void setKnowledgeBaseIds(List<Long> knowledgeBaseIds) {
        this.knowledgeBaseIds = knowledgeBaseIds;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public Integer getRerankTopN() {
        return rerankTopN;
    }

    public void setRerankTopN(Integer rerankTopN) {
        this.rerankTopN = rerankTopN;
    }

    public Double getRerankThreshold() {
        return rerankThreshold;
    }

    public void setRerankThreshold(Double rerankThreshold) {
        this.rerankThreshold = rerankThreshold;
    }
}

