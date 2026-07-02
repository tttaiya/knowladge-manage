package com.km.report.dto;

import lombok.Data;

@Data
public class GenerateReportRequest {
    private Long reportId;
    private Boolean stream;
}
