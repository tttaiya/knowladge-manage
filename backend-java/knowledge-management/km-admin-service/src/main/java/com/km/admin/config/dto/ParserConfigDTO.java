package com.km.admin.config.dto;

/**
 * 解析器配置 DTO（不含 API Key，仅运行时配置；Worker DynamicConfigHolder 热加载 maxConcurrentTasks）。
 */
public class ParserConfigDTO {
    private boolean paddleocrEnabled;
    private int maxConcurrentTasks;
    private int maxRetryCount;
    private int timeoutSeconds;

    public boolean isPaddleocrEnabled() { return paddleocrEnabled; }
    public void setPaddleocrEnabled(boolean paddleocrEnabled) { this.paddleocrEnabled = paddleocrEnabled; }

    public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
    public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = maxConcurrentTasks; }

    public int getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}