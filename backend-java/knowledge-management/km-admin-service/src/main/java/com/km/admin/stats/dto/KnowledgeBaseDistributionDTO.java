// dto/KnowledgeBaseDistributionDTO.java
package com.km.admin.stats.dto;

public class KnowledgeBaseDistributionDTO {
    private String category;
    private Long count;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}