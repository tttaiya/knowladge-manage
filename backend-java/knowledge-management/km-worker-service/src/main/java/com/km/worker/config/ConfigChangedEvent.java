package com.km.worker.config;

import java.util.Map;

/**
 * F6 系统配置变更事件（Worker 端 POJO；与 admin 端 com.km.admin.config.dto.ConfigChangedEvent 字段对齐）。
 *
 * R26：Worker 端用 byte[] + ObjectMapper 解析为该 POJO（不依赖 Jackson2JsonMessageConverter Bean）。
 * v6：统一事件类名为 ConfigChangedEvent（与 admin 端同名）。
 */
public class ConfigChangedEvent {
    private String eventId;
    private String occurredAt;
    private String source;
    private String configGroup;
    private Map<String, String> values;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getConfigGroup() { return configGroup; }
    public void setConfigGroup(String configGroup) { this.configGroup = configGroup; }

    public Map<String, String> getValues() { return values; }
    public void setValues(Map<String, String> values) { this.values = values; }
}