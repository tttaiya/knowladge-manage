// service/impl/StatsServiceImpl.java
package com.km.admin.stats.service.impl;

import com.km.admin.stats.dto.*;
import com.km.admin.stats.entity.*;
import com.km.admin.stats.mapper.*;
import com.km.admin.stats.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsServiceImpl.class);

    private final KnowledgeBaseStatsMapper knowledgeBaseStatsMapper;
    private final DocumentStatsMapper documentStatsMapper;
    private final DocumentChunkStatsMapper documentChunkStatsMapper;
    private final ProcessTaskStatsMapper processTaskStatsMapper;
    private final ReviewRecordStatsMapper reviewRecordStatsMapper;

    public StatsServiceImpl(KnowledgeBaseStatsMapper knowledgeBaseStatsMapper,
                            DocumentStatsMapper documentStatsMapper,
                            DocumentChunkStatsMapper documentChunkStatsMapper,
                            ProcessTaskStatsMapper processTaskStatsMapper,
                            ReviewRecordStatsMapper reviewRecordStatsMapper) {
        this.knowledgeBaseStatsMapper = knowledgeBaseStatsMapper;
        this.documentStatsMapper = documentStatsMapper;
        this.documentChunkStatsMapper = documentChunkStatsMapper;
        this.processTaskStatsMapper = processTaskStatsMapper;
        this.reviewRecordStatsMapper = reviewRecordStatsMapper;
    }

    @Override
    public StatsOverviewDTO getStatsOverview(Integer days) {
        log.info("获取统计概览数据，天数：{}", days);

        StatsOverviewDTO dto = new StatsOverviewDTO();

        // 1. 知识库统计
        KnowledgeBaseStats kbStats = knowledgeBaseStatsMapper.getKnowledgeBaseStats();
        if (kbStats != null) {
            dto.setKnowledgeBaseTotal(kbStats.getActiveCount());
        }

        // 2. 文档统计
        Long documentTotal = documentStatsMapper.getDocumentTotal();
        dto.setDocumentTotal(documentTotal != null ? documentTotal : 0L);

        Long failedCount = documentStatsMapper.getFailedDocumentCount();
        dto.setDocumentFailed(failedCount != null ? failedCount : 0L);

        // 3. 文档状态分布
        List<DocumentStats> docStatusList = documentStatsMapper.getDocumentStatusDistribution();
        if (docStatusList != null && !docStatusList.isEmpty()) {
            for (DocumentStats doc : docStatusList) {
                if (doc.getDocumentStatus() != null) {
                    switch (doc.getDocumentStatus()) {
                        case "READY":
                            dto.setDocumentReady(doc.getStatusCount());
                            break;
                        case "PENDING_REVIEW":
                            dto.setDocumentPendingReview(doc.getStatusCount());
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        // 4. 切片统计
        DocumentChunkStats chunkStats = documentChunkStatsMapper.getChunkStats();
        if (chunkStats != null) {
            dto.setChunkTotal(chunkStats.getTotalChunks() != null ? chunkStats.getTotalChunks() : 0L);
            dto.setChunkPendingVector(chunkStats.getPendingVectorCount() != null ? chunkStats.getPendingVectorCount() : 0L);
            dto.setChunkReadyVector(chunkStats.getReadyVectorCount() != null ? chunkStats.getReadyVectorCount() : 0L);
        }

        // 5. 任务统计
        Long queuedCount = processTaskStatsMapper.getQueuedTaskCount();
        dto.setTaskQueued(queuedCount != null ? queuedCount : 0L);

        Long runningCount = processTaskStatsMapper.getRunningTaskCount();
        dto.setTaskRunning(runningCount != null ? runningCount : 0L);

        Long successCount = processTaskStatsMapper.getSuccessTaskCount();
        dto.setTaskSuccess(successCount != null ? successCount : 0L);

        Long failedTaskCount = processTaskStatsMapper.getFailedTaskCount();
        dto.setTaskFailed(failedTaskCount != null ? failedTaskCount : 0L);

        // 计算成功率（只计算已结束的任务）
        long completedTotal = (successCount != null ? successCount : 0L) + (failedTaskCount != null ? failedTaskCount : 0L);
        if (completedTotal > 0) {
            double rate = (double) (successCount != null ? successCount : 0L) / completedTotal * 100;
            dto.setTaskSuccessRate(Math.round(rate * 100.0) / 100.0);
        } else {
            dto.setTaskSuccessRate(0.0);
        }

        // 6. 审核统计
        Long approvedCount = reviewRecordStatsMapper.getApprovedCount();
        dto.setReviewApproved(approvedCount != null ? approvedCount : 0L);

        Long rejectedCount = reviewRecordStatsMapper.getRejectedCount();
        dto.setReviewRejected(rejectedCount != null ? rejectedCount : 0L);

        // 7. 趋势数据
        List<DocumentStats> documentTrend = documentStatsMapper.getDocumentTrend(days);
        dto.setDocumentTrend(convertDocumentTrend(documentTrend));

        List<ProcessTaskStats> taskTrend = processTaskStatsMapper.getTaskTrend(days);
        dto.setTaskTrend(convertTaskTrend(taskTrend));

        List<ReviewRecordStats> reviewTrend = reviewRecordStatsMapper.getReviewTrend(days);
        dto.setReviewTrend(convertReviewTrend(reviewTrend));

        // 8. 分布数据
        List<KnowledgeBaseStats> kbDistribution = knowledgeBaseStatsMapper.getKnowledgeBaseDistribution();
        dto.setKnowledgeBaseDistribution(convertKBDistribution(kbDistribution));

        dto.setDocumentStatusDistribution(convertDocumentStatusDistribution(docStatusList));

        List<ProcessTaskStats> taskDistribution = processTaskStatsMapper.getTaskStatusDistribution();
        dto.setTaskStatusDistribution(convertTaskStatusDistribution(taskDistribution));

        return dto;
    }

    private List<TrendDataDTO> convertDocumentTrend(List<DocumentStats> documentStatsList) {
        if (documentStatsList == null || documentStatsList.isEmpty()) {
            return new ArrayList<>();
        }
        return documentStatsList.stream()
                .filter(stat -> stat.getUploadDate() != null)
                .map(stat -> {
                    TrendDataDTO trend = new TrendDataDTO();
                    trend.setDate(stat.getUploadDate().toLocalDate());
                    trend.setCount(stat.getDailyUploadCount() != null ? stat.getDailyUploadCount() : 0L);
                    trend.setType("UPLOAD");
                    return trend;
                })
                .collect(Collectors.toList());
    }

    private List<TrendDataDTO> convertTaskTrend(List<ProcessTaskStats> taskStatsList) {
        if (taskStatsList == null || taskStatsList.isEmpty()) {
            return new ArrayList<>();
        }
        return taskStatsList.stream()
                .filter(stat -> stat.getTaskDate() != null)
                .map(stat -> {
                    TrendDataDTO trend = new TrendDataDTO();
                    trend.setDate(stat.getTaskDate().toLocalDate());
                    trend.setCount(stat.getDailyTaskCount() != null ? stat.getDailyTaskCount() : 0L);
                    trend.setType("TASK");
                    return trend;
                })
                .collect(Collectors.toList());
    }

    private List<TrendDataDTO> convertReviewTrend(List<ReviewRecordStats> reviewStatsList) {
        if (reviewStatsList == null || reviewStatsList.isEmpty()) {
            return new ArrayList<>();
        }
        List<TrendDataDTO> trends = new ArrayList<>();
        for (ReviewRecordStats stat : reviewStatsList) {
            if (stat.getReviewDate() != null) {
                TrendDataDTO approved = new TrendDataDTO();
                approved.setDate(stat.getReviewDate().toLocalDate());
                approved.setCount(stat.getDailyApprovedCount() != null ? stat.getDailyApprovedCount() : 0L);
                approved.setType("REVIEW_APPROVED");
                trends.add(approved);

                TrendDataDTO rejected = new TrendDataDTO();
                rejected.setDate(stat.getReviewDate().toLocalDate());
                rejected.setCount(stat.getDailyRejectedCount() != null ? stat.getDailyRejectedCount() : 0L);
                rejected.setType("REVIEW_REJECTED");
                trends.add(rejected);
            }
        }
        return trends;
    }

    private List<KnowledgeBaseDistributionDTO> convertKBDistribution(List<KnowledgeBaseStats> kbStatsList) {
        if (kbStatsList == null || kbStatsList.isEmpty()) {
            return new ArrayList<>();
        }
        return kbStatsList.stream()
                .filter(stat -> stat.getCategory() != null)
                .map(stat -> {
                    KnowledgeBaseDistributionDTO dto = new KnowledgeBaseDistributionDTO();
                    dto.setCategory(stat.getCategory());
                    dto.setCount(stat.getCategoryCount() != null ? stat.getCategoryCount() : 0L);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<DocumentStatusDistributionDTO> convertDocumentStatusDistribution(List<DocumentStats> docStatsList) {
        if (docStatsList == null || docStatsList.isEmpty()) {
            return new ArrayList<>();
        }
        return docStatsList.stream()
                .filter(stat -> stat.getDocumentStatus() != null)
                .map(stat -> {
                    DocumentStatusDistributionDTO dto = new DocumentStatusDistributionDTO();
                    dto.setStatus(stat.getDocumentStatus());
                    dto.setStatusName(getStatusName(stat.getDocumentStatus()));
                    dto.setCount(stat.getStatusCount() != null ? stat.getStatusCount() : 0L);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<TaskStatusDistributionDTO> convertTaskStatusDistribution(List<ProcessTaskStats> taskStatsList) {
        if (taskStatsList == null || taskStatsList.isEmpty()) {
            return new ArrayList<>();
        }
        return taskStatsList.stream()
                .filter(stat -> stat.getTaskStatus() != null)
                .map(stat -> {
                    TaskStatusDistributionDTO dto = new TaskStatusDistributionDTO();
                    dto.setStatus(stat.getTaskStatus());
                    dto.setStatusName(getTaskStatusName(stat.getTaskStatus()));
                    dto.setCount(stat.getStatusCount() != null ? stat.getStatusCount() : 0L);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String getStatusName(String status) {
        if (status == null) return status;
        switch (status) {
            case "UPLOADED": return "已上传";
            case "PENDING_REVIEW": return "待审核";
            case "READY": return "已就绪";
            case "REVIEW_REJECTED": return "审核驳回";
            case "FAILED": return "失败";
            default: return status;
        }
    }

    private String getTaskStatusName(String status) {
        if (status == null) return status;
        switch (status) {
            case "QUEUED": return "排队中";
            case "RUNNING": return "运行中";
            case "SUCCESS": return "成功";
            case "FAILED": return "失败";
            default: return status;
        }
    }
}
