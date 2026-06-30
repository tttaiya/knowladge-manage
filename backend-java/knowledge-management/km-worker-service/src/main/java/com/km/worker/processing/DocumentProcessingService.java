package com.km.worker.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.worker.admin.AdminClient;
import com.km.worker.client.FastApiClient;
import com.km.worker.exception.AiStageException;
import com.km.worker.filestaging.TaskFileStagingService;
import com.km.worker.limits.HeartbeatHandle;
import com.km.worker.limits.HeartbeatService;
import com.km.worker.limits.PermitManager;
import com.km.worker.messaging.EventSeq;
import com.km.worker.messaging.KmTaskMessage;
import com.km.worker.messaging.KmTaskResultMessage;
import com.km.worker.purge.MinioPurgeClient;
import com.km.worker.queue.DelayRetryPublisher;
import com.km.worker.queue.TaskResultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F4 整合（commit #24）：文档处理服务（抽出独立文件，硬规则要求）。
 *
 * 关键变更（v1.0 文档 6.2 / 6.4）：
 * - PROCESS/REPROCESS：先 TaskFileStagingService.stage() 下载到 task-files 卷 → parse → chunk → embed
 * - 每次调用 FastApiClient 都已 assertSuccess（FastApiClient 内部）
 * - 失败事件携带 errorStage（AiStageException.errorStage）；成功/失败消息走 km.task.result
 * - finally 清理暂存文件
 *
 * 与原 WorkerApplication 内嵌 DocumentProcessingService 兼容（保留所有原行为 + 重试/限流/心跳/PURGE 逻辑）
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    @Autowired private PermitManager permitManager;
    @Autowired private AdminClient adminClient;
    @Autowired private HeartbeatService heartbeatService;
    @Autowired private FastApiClient fastApiClient;
    @Autowired private MinioPurgeClient minioPurgeClient;
    @Autowired private TaskResultProducer resultProducer;
    @Autowired private DelayRetryPublisher delayRetryPublisher;
    @Autowired private com.km.worker.DynamicConfigHolder configHolder;
    @Autowired private TaskFileStagingService taskFileStagingService;

    public void handle(KmTaskMessage msg, com.rabbitmq.client.Channel channel, long tag) throws Exception {
        String claimToken = UUID.randomUUID().toString().replace("-", "");
        boolean heavy = "PROCESS".equals(msg.taskType) || "REPROCESS".equals(msg.taskType);
        boolean permit = false;
        HeartbeatHandle heartbeat = null;
        String stagedFilePath = null;

        try {
            // F4 commit #23+24：PROCESS/REPROCESS 先下载到共享卷
            if (heavy) {
                stagedFilePath = taskFileStagingService.stage(
                        msg.taskId, msg.filePath, msg.extension);
            }

            if (heavy) {
                permit = permitManager.acquire(msg.taskId, claimToken, configHolder.maxConcurrentTasks());
                if (!permit) {
                    delayRetryPublisher.publish(msg);
                    channel.basicAck(tag, false);
                    return;
                }
            }
            if (!adminClient.claim(msg.taskId, claimToken)) {
                if (permit) permitManager.release(msg.taskId, claimToken);
                channel.basicAck(tag, false);
                return;
            }
            heartbeat = heartbeatService.start(msg.taskId, claimToken, permit);
            msg.claimToken = claimToken;

            if ("PROCESS".equals(msg.taskType) || "REPROCESS".equals(msg.taskType)) {
                // F4 commit #24：parse 用 stagedFilePath（容器内路径）；FastApiClient 已 assertSuccess
                resultProducer.publishStatus(msg, "PARSING", 20);
                Map<String, Object> parsed = fastApiClient.parse(msg, stagedFilePath);
                ensureLease(heartbeat);

                resultProducer.publishStatus(msg, "CHUNKING", 45);
                Map<String, Object> chunks = fastApiClient.chunk(msg, parsed);
                ensureLease(heartbeat);

                resultProducer.publishStatus(msg, "VECTORIZING", 75);
                Object parsedChunks = chunks.get("chunks");
                Map<String, Object> vectors = fastApiClient.embed(msg, parsedChunks);
                ensureLease(heartbeat);

                resultProducer.publishSuccess(msg, "TASK_SUCCESS", vectors);
            } else if ("REEMBED".equals(msg.taskType)) {
                resultProducer.publishStatus(msg, "VECTORIZING", 60);
                Map<String, Object> vectors = fastApiClient.reembed(msg);
                ensureLease(heartbeat);
                resultProducer.publishSuccess(msg, "REEMBED_RESULT", vectors);
            } else if ("PURGE".equals(msg.taskType)) {
                // R8 必改 4：PURGE 链路必须先 MinIO 再 ChromaDB
                resultProducer.publishStatus(msg, "PURGE", 30);
                boolean minioOk = minioPurgeClient.deleteObject(msg.filePath);
                ensureLease(heartbeat);
                boolean chromaOk = minioOk && fastApiClient.deleteVectors(msg.docId);
                ensureLease(heartbeat);
                if (minioOk && chromaOk) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("objectKey", msg.filePath);
                    payload.put("docId", msg.docId);
                    resultProducer.publishSuccess(msg, "PURGE_RESULT", payload);
                } else {
                    String stage = minioOk ? "CHROMA" : "MINIO";
                    String err = minioOk ? "FastAPI deleteVectors failed" : "MinIO deleteObject failed";
                    throw new IllegalStateException(stage + ": " + err);
                }
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            // F4 commit #24：提取 errorStage
            String stage = extractStage(e);
            String errMsg = shortMessage(e);
            if (msg.claimToken != null) {
                resultProducer.publishFailure(msg, "TASK_FAILED", stage, errMsg);
            }
            channel.basicAck(tag, false);
        } finally {
            if (heartbeat != null) heartbeat.stop();
            if (permit) permitManager.release(msg.taskId, claimToken);
            // F4 commit #23：清理暂存文件（即使失败也要清）
            if (stagedFilePath != null) {
                taskFileStagingService.cleanup(msg.taskId);
            }
        }
    }

    /**
     * F4 commit #24：refreshConfig 由 configChangedListener 调用，转发给 holder
     * （保留原行为：WorkerConsumers.configChanged 仍可调用 DocumentProcessingService.refreshConfig）
     */
    public void refreshConfig(String eventJson) {
        configHolder.refreshFromEvent(eventJson);
    }

    private void ensureLease(HeartbeatHandle heartbeat) {
        if (heartbeat != null && heartbeat.leaseLost.get()) {
            throw new IllegalStateException("Worker lease lost; wait for another worker to resume task");
        }
    }

    /**
     * F4 commit #24：从异常中提取阶段（AiStageException > 错误信息含 STAGE 关键字 > INTERNAL 兜底）
     */
    private String extractStage(Exception e) {
        if (e instanceof AiStageException) {
            return ((AiStageException) e).getErrorStage();
        }
        // 兜底：如果是 PURGE 链路抛出的 IllegalStateException（"STAGE: msg"）
        String msg = e.getMessage();
        if (msg != null) {
            for (String s : new String[]{"STAGING", "PARSE", "OCR", "CHUNK", "EMBED", "CHROMA", "MINIO"}) {
                if (msg.startsWith(s + ":") || msg.startsWith(s + " ")) {
                    return s;
                }
            }
        }
        return "INTERNAL";
    }

    private String stage(KmTaskMessage msg) {
        return "REEMBED".equals(msg.taskType) ? "VECTORIZING"
                : "PURGE".equals(msg.taskType) ? "PURGE" : "PROCESS";
    }

    private String shortMessage(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 900 ? msg.substring(0, 900) : msg;
    }
}