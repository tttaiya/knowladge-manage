// entity/ProcessTaskStats.java
package com.km.admin.stats.entity;

import java.time.LocalDateTime;

public class ProcessTaskStats {
    private Long queuedCount;
    private Long runningCount;
    private Long successCount;
    private Long failedCount;
    private Double successRate;
    private LocalDateTime taskDate;
    private Long dailyTaskCount;
    private String taskType;
    private String taskStatus;
    private Long statusCount;

    public Long getQueuedCount() {
        return queuedCount;
    }

    public void setQueuedCount(Long queuedCount) {
        this.queuedCount = queuedCount;
    }

    public Long getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(Long runningCount) {
        this.runningCount = runningCount;
    }

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    public LocalDateTime getTaskDate() {
        return taskDate;
    }

    public void setTaskDate(LocalDateTime taskDate) {
        this.taskDate = taskDate;
    }

    public Long getDailyTaskCount() {
        return dailyTaskCount;
    }

    public void setDailyTaskCount(Long dailyTaskCount) {
        this.dailyTaskCount = dailyTaskCount;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public Long getStatusCount() {
        return statusCount;
    }

    public void setStatusCount(Long statusCount) {
        this.statusCount = statusCount;
    }
}