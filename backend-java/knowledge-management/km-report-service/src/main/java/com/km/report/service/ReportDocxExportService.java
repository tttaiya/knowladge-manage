package com.km.report.service;

import com.km.report.vo.ReportExportResultVO;

import javax.servlet.http.HttpServletResponse;

public interface ReportDocxExportService {

    /**
     * 基于已保存的报告数据重新生成 Word，不重新执行 AI
     */
    ReportExportResultVO regenerateDocx(Long reportId);

    /**
     * 下载已生成的报告文件
     */
    void downloadFile(String fileName, HttpServletResponse response);
}