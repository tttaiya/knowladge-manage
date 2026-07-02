package com.km.report.dto;

import lombok.Data;

@Data
public class TemplateQueryDTO {
    private String keyword;
    private String reportType;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
