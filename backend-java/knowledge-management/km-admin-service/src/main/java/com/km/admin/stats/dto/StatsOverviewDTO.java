package com.km.admin.stats.dto;

import java.util.List;

/**
 * F8 数据统计概览 DTO。
 *
 * <p>字段语义：
 * <ul>
 *   <li>knowledgeBaseTotal — km_knowledge_base where is_deleted=0</li>
 *   <li>documentTotal — km_document where is_deleted=0</li>
 *   <li>chunkTotal — km_document_chunk join km_document where chunk.is_active=1 and doc.is_deleted=0</li>
 *   <li>documentReady — km_document where is_deleted=0 and document_status='READY'</li>
 *   <li>documentPendingReview — km_document where is_deleted=0 and document_status='PENDING_REVIEW'</li>
 *   <li>documentFailed — km_document where is_deleted=0 and document_status='FAILED'</li>
 *   <li>taskProcessing — km_document_process_task where task_status in ('QUEUED','RUNNING')</li>
 *   <li>documentTrend — 按 created_at 日期分组的文档新增趋势,长度 = days(服务端补零)</li>
 * </ul>
 */
public class StatsOverviewDTO {

    private long knowledgeBaseTotal;
    private long documentTotal;
    private long chunkTotal;
    private long documentReady;
    private long documentPendingReview;
    private long documentFailed;
    private long taskProcessing;
    private List<TrendDataDTO> documentTrend;

    public long getKnowledgeBaseTotal() { return knowledgeBaseTotal; }
    public void setKnowledgeBaseTotal(long knowledgeBaseTotal) { this.knowledgeBaseTotal = knowledgeBaseTotal; }

    public long getDocumentTotal() { return documentTotal; }
    public void setDocumentTotal(long documentTotal) { this.documentTotal = documentTotal; }

    public long getChunkTotal() { return chunkTotal; }
    public void setChunkTotal(long chunkTotal) { this.chunkTotal = chunkTotal; }

    public long getDocumentReady() { return documentReady; }
    public void setDocumentReady(long documentReady) { this.documentReady = documentReady; }

    public long getDocumentPendingReview() { return documentPendingReview; }
    public void setDocumentPendingReview(long documentPendingReview) { this.documentPendingReview = documentPendingReview; }

    public long getDocumentFailed() { return documentFailed; }
    public void setDocumentFailed(long documentFailed) { this.documentFailed = documentFailed; }

    public long getTaskProcessing() { return taskProcessing; }
    public void setTaskProcessing(long taskProcessing) { this.taskProcessing = taskProcessing; }

    public List<TrendDataDTO> getDocumentTrend() { return documentTrend; }
    public void setDocumentTrend(List<TrendDataDTO> documentTrend) { this.documentTrend = documentTrend; }
}