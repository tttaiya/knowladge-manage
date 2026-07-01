package com.km.search.config;

import java.util.Map;

public class ConfigChangedEvent {
    private String eventId;
    private Long configVersion;
    private String occurredAt;
    private String source;
    private String configGroup;
    private Map<String, String> values;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }

    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getConfigGroup() { return configGroup; }
    public void setConfigGroup(String configGroup) { this.configGroup = configGroup; }

    public Map<String, String> getValues() { return values; }
    public void setValues(Map<String, String> values) { this.values = values; }
}
