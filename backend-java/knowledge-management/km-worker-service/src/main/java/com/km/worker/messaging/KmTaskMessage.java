package com.km.worker.messaging;

import java.util.List;
import java.util.Map;

/**
 * F4 整合（commit #24）：Worker 任务消息 DTO（抽出独立文件，硬规则要求）。
 *
 * 原为 WorkerApplication.java 单文件内嵌类，本次抽到独立文件以支持：
 * - FastApiClient 接收任务消息
 * - DocumentProcessingService 处理任务消息
 * - 后续 commit #17b ConfigStartupInitializer 等模块按需引用
 */
public class KmTaskMessage {
    public Long taskId;
    public Long docId;
    public Long kbId;
    public String taskType;
    public String triggerSource;
    public String traceId;
    public String filePath;       // MinIO object key（Worker 内部） / 容器内路径（FastAPI 调用时）
    public String extension;      // 文件扩展名（commit #23+24：F4 需要）
    public Long chunkId;
    public Long strategyVersion;
    public Long targetVersionNo;
    public String taskPayloadJson;
    public String claimToken;
}