package com.km.report.dto;

import lombok.Data;

@Data
public class ReportPageQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private String reportType;
    private Integer status;
}
