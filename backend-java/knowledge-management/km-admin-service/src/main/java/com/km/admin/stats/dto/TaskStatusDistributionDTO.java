// dto/TaskStatusDistributionDTO.java
package com.km.admin.stats.dto;

public class TaskStatusDistributionDTO {
    private String status;
    private String statusName;
    private Long count;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}