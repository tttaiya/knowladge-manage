package com.km.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.config.dto.ConfigChangedEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 配置变更事件发布器。
 *
 * R22：MQ 消息发布必须指定 Exchange：convertAndSend(exchange, routingKey, msg)，禁止走默认空 exchange。
 * R24：发布实际由 Service 在 TransactionSynchronizationManager.afterCommit 里调用，避免回滚误发。
 * R26：使用 ObjectMapper 显式序列化 JSON 字符串发出去，与 Worker 端 byte[] + ObjectMapper 反序列化对齐。
 *      不依赖 RabbitTemplate 全局 MessageConverter（项目其它 queue 仍走 SimpleMessageConverter + 手写 JSON）。
 */
@Component
public class ConfigChangedProducer {

    private static final String EXCHANGE = "km.exchange";
    private static final String ROUTING_KEY = "km.config.changed";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public void publishConfigChanged(String configGroup, Map<String, String> values) {
        ConfigChangedEvent event = new ConfigChangedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setOccurredAt(Instant.now().toString());
        event.setSource("km-admin");
        event.setConfigGroup(configGroup);
        event.setValues(values); // embedding/rerank: null；parser: 安全快照（不含 api_key）

        try {
            String json = objectMapper.writeValueAsString(event);
            CorrelationData cd = new CorrelationData("config-" + event.getEventId());
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, json.getBytes("UTF-8"), cd);
            // 发布确认：失败抛异常（与项目其它 producer 一致）
            CorrelationData.Confirm confirm = cd.getFuture().get(10, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "config event confirm timeout" : confirm.getReason());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish config event", e);
        }
    }
}