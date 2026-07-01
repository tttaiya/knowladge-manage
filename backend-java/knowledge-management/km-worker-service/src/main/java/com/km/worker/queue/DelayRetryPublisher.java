package com.km.worker.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.worker.messaging.KmTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * F4 整合（commit #24）：延迟重试发布器（抽出独立文件）。
 * 原为 WorkerApplication.java 单文件内嵌类。
 */
@Service
public class DelayRetryPublisher {
    private static final Logger log = LoggerFactory.getLogger(DelayRetryPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public DelayRetryPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(KmTaskMessage msg) {
        try {
            String routing = "REPROCESS".equals(msg.taskType) ? "km.doc.reprocess.retry" : "km.doc.process.retry";
            CorrelationData cd = new CorrelationData("retry-" + msg.taskId + "-" + System.currentTimeMillis());
            rabbitTemplate.send("km.exchange", routing, MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(msg))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .build(), cd);
            CorrelationData.Confirm confirm = cd.getFuture().get(10, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException("retry confirm failed");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
