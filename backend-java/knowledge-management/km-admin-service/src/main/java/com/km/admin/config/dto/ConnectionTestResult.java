package com.km.admin.config.dto;

/**
 * 连接测试结果 DTO。
 */
public class ConnectionTestResult {
    private boolean success;
    private String message;
    private Long latencyMs;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
}