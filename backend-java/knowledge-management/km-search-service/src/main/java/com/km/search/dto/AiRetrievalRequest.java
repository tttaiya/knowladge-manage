package com.km.search.dto;

import java.util.List;

public class AiRetrievalRequest {

    private String query;
    private List<Long> knowledgeBaseIds;
    private List<Long> docIds;
    private List<String> tags;
    private String mode;
    private Integer topK;
    private Integer candidateK;
    private Double similarityThreshold;
    private String embeddingModel;
    private String embeddingApiBase;
    private String rerankModel;
    private String rerankApiBase;
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

    public List<Long> getDocIds() {
        return docIds;
    }

    public void setDocIds(List<Long> docIds) {
        this.docIds = docIds;
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

    public Integer getCandidateK() {
        return candidateK;
    }

    public void setCandidateK(Integer candidateK) {
        this.candidateK = candidateK;
    }

    public Double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingApiBase() {
        return embeddingApiBase;
    }

    public void setEmbeddingApiBase(String embeddingApiBase) {
        this.embeddingApiBase = embeddingApiBase;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public void setRerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
    }

    public String getRerankApiBase() {
        return rerankApiBase;
    }

    public void setRerankApiBase(String rerankApiBase) {
        this.rerankApiBase = rerankApiBase;
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

