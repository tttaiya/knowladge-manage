package com.km.report.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardOverviewVO {

    /**
     * 报告模板数量
     */
    private Long templateCount;

    /**
     * 报告生成记录数量
     */
    private Long reportRecordCount;

    /**
     * 生成成功数量
     */
    private Long successRecordCount;

    /**
     * 生成失败数量
     */
    private Long failedRecordCount;

    /**
     * 生成中数量
     */
    private Long generatingRecordCount;

    /**
     * 近30天生成趋势
     */
    private List<ReportGenerateTrendVO> trendList;
}