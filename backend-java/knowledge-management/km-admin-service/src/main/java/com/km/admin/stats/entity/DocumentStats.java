// entity/DocumentStats.java
package com.km.admin.stats.entity;

import java.time.LocalDateTime;

public class DocumentStats {
    private Long totalCount;
    private String documentStatus;
    private Long statusCount;
    private LocalDateTime uploadDate;
    private Long dailyUploadCount;
    private Long failedCount;

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public String getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(String documentStatus) {
        this.documentStatus = documentStatus;
    }

    public Long getStatusCount() {
        return statusCount;
    }

    public void setStatusCount(Long statusCount) {
        this.statusCount = statusCount;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public Long getDailyUploadCount() {
        return dailyUploadCount;
    }

    public void setDailyUploadCount(Long dailyUploadCount) {
        this.dailyUploadCount = dailyUploadCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }
}