// dto/StatsOverviewDTO.java
package com.km.admin.stats.dto;

import java.util.List;

public class StatsOverviewDTO {
    // 知识库统计
    private Long knowledgeBaseTotal;

    // 文档统计
    private Long documentTotal;
    private Long documentReady;
    private Long documentPendingReview;
    private Long documentFailed;

    // 切片统计
    private Long chunkTotal;
    private Long chunkPendingVector;
    private Long chunkReadyVector;

    // 任务统计
    private Long taskQueued;
    private Long taskRunning;
    private Long taskSuccess;
    private Long taskFailed;
    private Double taskSuccessRate;

    // 审核统计
    private Long reviewApproved;
    private Long reviewRejected;

    // 趋势数据
    private List<TrendDataDTO> documentTrend;
    private List<TrendDataDTO> taskTrend;
    private List<TrendDataDTO> reviewTrend;

    // 分布数据
    private List<KnowledgeBaseDistributionDTO> knowledgeBaseDistribution;
    private List<DocumentStatusDistributionDTO> documentStatusDistribution;
    private List<TaskStatusDistributionDTO> taskStatusDistribution;

    // Getters and Setters
    public Long getKnowledgeBaseTotal() {
        return knowledgeBaseTotal;
    }

    public void setKnowledgeBaseTotal(Long knowledgeBaseTotal) {
        this.knowledgeBaseTotal = knowledgeBaseTotal;
    }

    public Long getDocumentTotal() {
        return documentTotal;
    }

    public void setDocumentTotal(Long documentTotal) {
        this.documentTotal = documentTotal;
    }

    public Long getDocumentReady() {
        return documentReady;
    }

    public void setDocumentReady(Long documentReady) {
        this.documentReady = documentReady;
    }

    public Long getDocumentPendingReview() {
        return documentPendingReview;
    }

    public void setDocumentPendingReview(Long documentPendingReview) {
        this.documentPendingReview = documentPendingReview;
    }

    public Long getDocumentFailed() {
        return documentFailed;
    }

    public void setDocumentFailed(Long documentFailed) {
        this.documentFailed = documentFailed;
    }

    public Long getChunkTotal() {
        return chunkTotal;
    }

    public void setChunkTotal(Long chunkTotal) {
        this.chunkTotal = chunkTotal;
    }

    public Long getChunkPendingVector() {
        return chunkPendingVector;
    }

    public void setChunkPendingVector(Long chunkPendingVector) {
        this.chunkPendingVector = chunkPendingVector;
    }

    public Long getChunkReadyVector() {
        return chunkReadyVector;
    }

    public void setChunkReadyVector(Long chunkReadyVector) {
        this.chunkReadyVector = chunkReadyVector;
    }

    public Long getTaskQueued() {
        return taskQueued;
    }

    public void setTaskQueued(Long taskQueued) {
        this.taskQueued = taskQueued;
    }

    public Long getTaskRunning() {
        return taskRunning;
    }

    public void setTaskRunning(Long taskRunning) {
        this.taskRunning = taskRunning;
    }

    public Long getTaskSuccess() {
        return taskSuccess;
    }

    public void setTaskSuccess(Long taskSuccess) {
        this.taskSuccess = taskSuccess;
    }

    public Long getTaskFailed() {
        return taskFailed;
    }

    public void setTaskFailed(Long taskFailed) {
        this.taskFailed = taskFailed;
    }

    public Double getTaskSuccessRate() {
        return taskSuccessRate;
    }

    public void setTaskSuccessRate(Double taskSuccessRate) {
        this.taskSuccessRate = taskSuccessRate;
    }

    public Long getReviewApproved() {
        return reviewApproved;
    }

    public void setReviewApproved(Long reviewApproved) {
        this.reviewApproved = reviewApproved;
    }

    public Long getReviewRejected() {
        return reviewRejected;
    }

    public void setReviewRejected(Long reviewRejected) {
        this.reviewRejected = reviewRejected;
    }

    public List<TrendDataDTO> getDocumentTrend() {
        return documentTrend;
    }

    public void setDocumentTrend(List<TrendDataDTO> documentTrend) {
        this.documentTrend = documentTrend;
    }

    public List<TrendDataDTO> getTaskTrend() {
        return taskTrend;
    }

    public void setTaskTrend(List<TrendDataDTO> taskTrend) {
        this.taskTrend = taskTrend;
    }

    public List<TrendDataDTO> getReviewTrend() {
        return reviewTrend;
    }

    public void setReviewTrend(List<TrendDataDTO> reviewTrend) {
        this.reviewTrend = reviewTrend;
    }

    public List<KnowledgeBaseDistributionDTO> getKnowledgeBaseDistribution() {
        return knowledgeBaseDistribution;
    }

    public void setKnowledgeBaseDistribution(List<KnowledgeBaseDistributionDTO> knowledgeBaseDistribution) {
        this.knowledgeBaseDistribution = knowledgeBaseDistribution;
    }

    public List<DocumentStatusDistributionDTO> getDocumentStatusDistribution() {
        return documentStatusDistribution;
    }

    public void setDocumentStatusDistribution(List<DocumentStatusDistributionDTO> documentStatusDistribution) {
        this.documentStatusDistribution = documentStatusDistribution;
    }

    public List<TaskStatusDistributionDTO> getTaskStatusDistribution() {
        return taskStatusDistribution;
    }

    public void setTaskStatusDistribution(List<TaskStatusDistributionDTO> taskStatusDistribution) {
        this.taskStatusDistribution = taskStatusDistribution;
    }
}
