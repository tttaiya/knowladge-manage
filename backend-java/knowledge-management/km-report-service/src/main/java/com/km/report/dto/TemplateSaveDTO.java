package com.km.report.dto;

import lombok.Data;

@Data
public class TemplateSaveDTO {
    private Long id;
    private String templateName;
    private String reportType;
    private String description;
    private String templateScope;
    private String styleConfig;
    private String originalFileName;
    private String fileUrl;
    private Long fileSize;
}
