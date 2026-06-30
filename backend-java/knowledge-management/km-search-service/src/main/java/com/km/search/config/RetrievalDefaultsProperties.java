package com.km.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "km.retrieval")
public class RetrievalDefaultsProperties {

    private String defaultMode = "VECTOR_RERANK";
    private int defaultTopK = 5;
    private double defaultSimilarityThreshold = 0.30;
    private int defaultRerankTopN = 3;
    private double defaultRerankThreshold = 0.50;
    private int maxTopK = 50;
    private int maxCandidateK = 200;

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public double getDefaultSimilarityThreshold() {
        return defaultSimilarityThreshold;
    }

    public void setDefaultSimilarityThreshold(double defaultSimilarityThreshold) {
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
    }

    public int getDefaultRerankTopN() {
        return defaultRerankTopN;
    }

    public void setDefaultRerankTopN(int defaultRerankTopN) {
        this.defaultRerankTopN = defaultRerankTopN;
    }

    public double getDefaultRerankThreshold() {
        return defaultRerankThreshold;
    }

    public void setDefaultRerankThreshold(double defaultRerankThreshold) {
        this.defaultRerankThreshold = defaultRerankThreshold;
    }

    public int getMaxTopK() {
        return maxTopK;
    }

    public void setMaxTopK(int maxTopK) {
        this.maxTopK = maxTopK;
    }

    public int getMaxCandidateK() {
        return maxCandidateK;
    }

    public void setMaxCandidateK(int maxCandidateK) {
        this.maxCandidateK = maxCandidateK;
    }
}

