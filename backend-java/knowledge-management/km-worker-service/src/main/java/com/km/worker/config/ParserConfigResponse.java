package com.km.worker.config;

/**
 * F6（commit #17b）：Worker 端本地 ParserConfig DTO。
 *
 * R33 修正：Worker 不依赖 Admin 模块的 ParserConfigDTO；字段对齐即可，类型独立。
 * 仅保留 Worker 启动初始化需要的 maxConcurrentTasks 字段（其它字段 Worker 不读）。
 */
public class ParserConfigResponse {
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