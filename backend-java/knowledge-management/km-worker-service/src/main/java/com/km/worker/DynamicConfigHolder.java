package com.km.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * F6（commit #17a）：系统配置持有器（Worker 全局并发上限）。
 *
 * 职责：
 * - 持有 parser.max_concurrent_tasks（volatile int）
 * - 标记初始化状态（volatile boolean）
 * - 提供 R28 Worker Claim 双重限流的"全局并发"判断依据
 * - 解析 ConfigChangedEvent JSON（commit #17a 重写，替代 buggy 的 Integer.getInteger 系统属性读法）
 *
 * 历史说明：
 * - 原本与 4 个 Worker listener 一起堆在 WorkerApplication.java 单文件里
 * - commit #17a 重写 refreshFromEvent + 加 initialized 标志 + 加范围校验
 * - commit #17b 抽出到独立文件（解决 WorkerApplication 单文件过大 + 暴露 public API 给 ConfigStartupInitializer）
 */
@Service
public class DynamicConfigHolder {

    private final ObjectMapper objectMapper;
    private volatile int maxConcurrentTasks;
    private volatile boolean initialized = false;

    public DynamicConfigHolder(ObjectMapper objectMapper, @Value("${km.max-concurrent-tasks:2}") int maxConcurrentTasks) {
        this.objectMapper = objectMapper;
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    public int maxConcurrentTasks() {
        // R32：未初始化直接抛异常；ConfigStartupInitializer 必须先 markInitialized()
        if (!initialized) {
            throw new IllegalStateException(
                    "DynamicConfigHolder not initialized. Worker startup must load config before consuming.");
        }
        return Math.max(1, maxConcurrentTasks);
    }

    public void setMaxConcurrentTasks(int n) {
        if (n < 1 || n > 100) {
            throw new IllegalArgumentException("maxConcurrentTasks out of range [1, 100]: " + n);
        }
        this.maxConcurrentTasks = n;
    }

    public void markInitialized() {
        this.initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * R25：解析 event JSON，提取 configGroup + values.parser.max_concurrent_tasks。
     * 非 parser 事件或数值非法 → 抛 AmqpRejectAndDontRequeueException → DLQ。
     */
    public void refreshFromEvent(String eventJson) {
        if (eventJson == null || eventJson.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(eventJson, Map.class);
            String group = (String) map.get("configGroup");
            if (!"parser".equals(group)) {
                return;
            }
            Object values = map.get("values");
            if (!(values instanceof Map)) {
                return;
            }
            Object max = ((Map) values).get("parser.max_concurrent_tasks");
            if (max == null) {
                return;
            }
            int n = Integer.parseInt(max.toString());
            setMaxConcurrentTasks(n); // 范围校验
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // R26：JSON 解析失败抛 AmqpRejectAndDontRequeueException → DLQ
            throw new AmqpRejectAndDontRequeueException("Invalid config event JSON", e);
        } catch (IllegalArgumentException e) {
            // R26：NumberFormatException 是 IllegalArgumentException 子类，统一捕获
            // 数值非法 / 范围非法 → 走 DLQ
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid config event value: " + e.getMessage(), e);
        }
    }
}