package com.km.report.vo;

import lombok.Data;

@Data
public class ReportGenerateTrendVO {

    /**
     * 日期，格式 yyyy-MM-dd
     */
    private String date;

    /**
     * 当日报告生成数量
     */
    private Long count;
}