package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.service.ReportDocxExportService;
import com.km.report.vo.ReportExportResultVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/v1/reports/records")
public class ReportExportController {

    @Resource
    private ReportDocxExportService reportDocxExportService;

    @GetMapping("/{reportId}/export/docx/regenerate")
    public ApiResult<ReportExportResultVO> regenerateDocx(@PathVariable Long reportId) {
        return ApiResult.ok(reportDocxExportService.regenerateDocx(reportId));
    }
}
