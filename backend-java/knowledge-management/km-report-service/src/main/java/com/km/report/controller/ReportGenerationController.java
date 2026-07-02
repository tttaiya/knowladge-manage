package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.dto.GenerateReportRequest;
import com.km.report.vo.ReportGenerationProgressVO;
import com.km.report.service.ReportGenerationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/v1/reports/generation")
public class ReportGenerationController {

    @Resource
    private ReportGenerationService reportGenerationService;

    @PostMapping
    public ApiResult<ReportGenerationProgressVO> start(@RequestBody GenerateReportRequest request) {
        return ApiResult.ok(reportGenerationService.startGenerate(request));
    }

    @GetMapping("/{reportId}/progress")
    public ApiResult<ReportGenerationProgressVO> progress(@PathVariable Long reportId) {
        return ApiResult.ok(reportGenerationService.getProgress(reportId));
    }
}
