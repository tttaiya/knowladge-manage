package com.km.worker.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.worker.messaging.EventSeq;
import com.km.worker.messaging.KmTaskMessage;
import com.km.worker.messaging.KmTaskResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * F4 整合（commit #24）：任务结果事件生产者（抽出独立文件）。
 * 原为 WorkerApplication.java 单文件内嵌类。
 */
@Service
public class TaskResultProducer {
    private static final Logger log = LoggerFactory.getLogger(TaskResultProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public TaskResultProducer(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishStatus(KmTaskMessage task, String stage, int progress) {
        KmTaskResultMessage m = base(task, "TASK_STATUS");
        m.stage = stage;
        m.progress = progress;
        send(m);
    }

    public void publishSuccess(KmTaskMessage task, String eventType, Map<String, Object> payload) {
        KmTaskResultMessage m = base(task, eventType);
        m.success = true;
        m.stage = eventType;
        m.progress = 100;
        m.targetVersionNo = task.targetVersionNo;
        Object vectors = payload.get("vectorIds");
        if (vectors instanceof List) {
            m.vectorIds = (List<String>) vectors;
        }
        Object chunks = payload.get("chunks");
        if (chunks instanceof List) {
            m.chunks = (List<Map<String, Object>>) chunks;
        }
        Object objectKey = payload.get("objectKey");
        m.objectKey = objectKey == null ? task.filePath : String.valueOf(objectKey);
        send(m);
    }

    public void publishFailure(KmTaskMessage task, String eventType, String stage, String errorMessage) {
        KmTaskResultMessage m = base(task, eventType);
        m.success = false;
        m.stage = stage;
        // F4 commit #24：errorStage 用于 Admin 状态机定位阶段化错误
        m.errorStage = stage;
        m.errorMessage = errorMessage;
        send(m);
    }

    private KmTaskResultMessage base(KmTaskMessage task, String eventType) {
        KmTaskResultMessage m = new KmTaskResultMessage();
        m.eventId = UUID.randomUUID().toString().replace("-", "");
        m.eventSeq = EventSeq.next(task.taskId);
        m.eventType = eventType;
        m.taskId = task.taskId;
        m.docId = task.docId;
        m.taskType = task.taskType;
        m.traceId = task.traceId;
        m.claimToken = task.claimToken;
        m.taskPayloadJson = task.taskPayloadJson;
        return m;
    }

    private void send(KmTaskResultMessage msg) {
        try {
            CorrelationData cd = new CorrelationData("result-" + msg.eventId);
            rabbitTemplate.send("km.exchange", "km.task.result", MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(msg))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .build(), cd);
            CorrelationData.Confirm confirm = cd.getFuture().get(10, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "result confirm timeout" : confirm.getReason());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
