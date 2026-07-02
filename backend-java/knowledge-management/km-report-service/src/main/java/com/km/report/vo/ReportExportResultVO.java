package com.km.report.vo;

import lombok.Data;

@Data
public class ReportExportResultVO {

    /**
     * 导出任务ID
     */
    private Long exportTaskId;

    /**
     * 报告ID
     */
    private Long reportId;

    /**
     * 文件访问地址
     */
    private String fileUrl;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 提示信息
     */
    private String message;
}