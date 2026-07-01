// entity/ReviewRecordStats.java
package com.km.admin.stats.entity;

import java.time.LocalDateTime;

public class ReviewRecordStats {
    private Long approvedCount;
    private Long rejectedCount;
    private LocalDateTime reviewDate;
    private Long dailyApprovedCount;
    private Long dailyRejectedCount;

    public Long getApprovedCount() {
        return approvedCount;
    }

    public void setApprovedCount(Long approvedCount) {
        this.approvedCount = approvedCount;
    }

    public Long getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(Long rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public LocalDateTime getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(LocalDateTime reviewDate) {
        this.reviewDate = reviewDate;
    }

    public Long getDailyApprovedCount() {
        return dailyApprovedCount;
    }

    public void setDailyApprovedCount(Long dailyApprovedCount) {
        this.dailyApprovedCount = dailyApprovedCount;
    }

    public Long getDailyRejectedCount() {
        return dailyRejectedCount;
    }

    public void setDailyRejectedCount(Long dailyRejectedCount) {
        this.dailyRejectedCount = dailyRejectedCount;
    }
}