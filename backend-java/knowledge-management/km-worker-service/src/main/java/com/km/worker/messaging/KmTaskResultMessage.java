package com.km.worker.messaging;

import java.util.List;
import java.util.Map;

/**
 * F4 整合（commit #24）：Worker 任务结果消息 DTO（抽出独立文件，硬规则要求）。
 *
 * 与 km-task-result 队列 payload 字段对齐。
 */
public class KmTaskResultMessage {
    public String eventId;
    public int eventSeq;
    public String eventType;
    public Long taskId;
    public Long docId;
    public String taskType;
    public String traceId;
    public String claimToken;
    public Boolean success;
    public Integer progress;
    public String stage;
    public String errorStage;
    public String errorMessage;
    public List<Map<String, Object>> chunks;
    public List<String> vectorIds;
    public String taskPayloadJson;
    public Long targetVersionNo;
    public String objectKey;
}