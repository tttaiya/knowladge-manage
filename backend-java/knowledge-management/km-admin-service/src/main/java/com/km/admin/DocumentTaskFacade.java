package com.km.admin;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档模块对任务层的公开门面。R16：与 TaskCommandService 同包，跨子包可注入。
 * 必改 3：内部委托方法名是真实方法名（createUserRetryTask）。
 *
 * <p>必须独立成文件（Java 语法要求 public 顶级类与文件名一致）。
 */
@Service
public class DocumentTaskFacade {

    private final TaskCommandService taskCommandService;

    public DocumentTaskFacade(TaskCommandService taskCommandService) {
        this.taskCommandService = taskCommandService;
    }

    public Long createProcessTask(Long docId, String userId) {
        return taskCommandService.createProcessTask(docId, userId);
    }

    public Long createPurgeTask(Long docId, LocalDateTime deletedAt) {
        return taskCommandService.createPurgeTask(docId, deletedAt);
    }

    public Long createRetryTask(Long docId, String userId) {
        return taskCommandService.createUserRetryTask(docId, userId);
    }

    public Long createReembedTask(Long docId, Long chunkId, String operatorUserId, Long chunkContentVersion) {
        return taskCommandService.createReembedTask(docId, chunkId, operatorUserId, chunkContentVersion);
    }

    public Long createReviewRejectedReprocessTask(Long docId, Long sourceReviewId, String operatorUserId) {
        return taskCommandService.createReviewRejectedReprocessTask(docId, sourceReviewId, operatorUserId);
    }

    public Long createStrategyReprocessTask(Long docId, Long strategyVersion, String operatorUserId) {
        return taskCommandService.createStrategyReprocessTask(docId, strategyVersion, operatorUserId);
    }

    public Long createKnowledgeBaseReprocessTask(Long docId, Long strategyVersion, String operatorUserId,
                                                 String triggerSource, String idempotencyKey,
                                                 Map<String, Object> payload) {
        return taskCommandService.createKnowledgeBaseReprocessTask(
                docId, strategyVersion, operatorUserId, triggerSource, idempotencyKey, payload);
    }
}
