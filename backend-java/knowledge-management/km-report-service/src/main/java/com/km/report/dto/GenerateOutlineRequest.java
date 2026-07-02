package com.km.report.dto;

import lombok.Data;

@Data
public class GenerateOutlineRequest {
    private String theme;
    private Long templateId;
    private String reportType;
    private String major;
    private String powerPlant;
    private Integer reportYear;
    private Integer fixedTemplate;
    private Integer enableKnowledgeRetrieval;
}
