package com.km.admin.config.entity;

import java.time.LocalDateTime;

/**
 * 系统配置实体（对应 km_system_config 表）。
 *
 * R11：实体以 SQL 为准；R25：API Key 字段不入 MQ 事件。
 */
public class SystemConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}