package com.km.report.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateVO {
    private Long id;
    private String templateName;
    private String reportType;
    private String description;
    private Integer status;
    private String templateScope;
    private Integer chapterCount;
    private String styleConfig;
    private String originalFileName;
    private String fileUrl;
    private Long fileSize;
    private LocalDateTime updateTime;
}
