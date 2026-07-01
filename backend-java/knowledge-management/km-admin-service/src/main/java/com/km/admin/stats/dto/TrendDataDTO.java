// dto/TrendDataDTO.java
package com.km.admin.stats.dto;

import java.time.LocalDate;

public class TrendDataDTO {
    private LocalDate date;
    private Long count;
    private String type;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
