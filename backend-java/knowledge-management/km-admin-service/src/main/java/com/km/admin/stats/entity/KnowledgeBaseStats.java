// entity/KnowledgeBaseStats.java
package com.km.admin.stats.entity;

import java.time.LocalDateTime;

public class KnowledgeBaseStats {
    private Long totalCount;
    private Long activeCount;
    private LocalDateTime createTime;
    private String category;// 实际对应 retrieval_strategy
    private Long categoryCount;

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(Long activeCount) {
        this.activeCount = activeCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getCategoryCount() {
        return categoryCount;
    }

    public void setCategoryCount(Long categoryCount) {
        this.categoryCount = categoryCount;
    }
}