package com.km.admin.config.dto;

import java.util.Map;

/**
 * 配置变更事件（发往 km.exchange / routing_key=km.config.changed）。
 *
 * R25：MQ 事件永远不携带 api_key。values 内 key 若以 .api_key 结尾会在 collectSafeConfigValues 阶段被过滤。
 * embedding/rerank 事件 values=null（仅发 configGroup 给监听者表明来源组）。
 * parser 事件 values=安全快照（含 parser.max_concurrent_tasks / retry / timeout 等）。
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