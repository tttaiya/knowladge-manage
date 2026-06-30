package com.km.search.dto;

public class AiRetrievalCandidate {

    private Long chunkId;
    private String vectorId;
    private Double distance;
    private Double similarityScore;
    private Double rerankScore;

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public Double getRerankScore() {
        return rerankScore;
    }

    public void setRerankScore(Double rerankScore) {
        this.rerankScore = rerankScore;
    }

    public double normalizedSimilarity() {
        if (similarityScore != null) {
            return similarityScore;
        }
        if (distance != null) {
            return 1.0D - distance;
        }
        return 0.0D;
    }
}

