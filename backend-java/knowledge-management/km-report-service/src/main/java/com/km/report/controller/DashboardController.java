package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.service.DashboardService;
import com.km.report.vo.DashboardOverviewVO;
import com.km.report.vo.ReportGenerateTrendVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/dashboard")
public class DashboardController {

    @Resource
    private DashboardService dashboardService;

    /**
     * 后台概览
     */
    @GetMapping("/overview")
    public ApiResult<DashboardOverviewVO> overview() {
        return ApiResult.ok(dashboardService.overview());
    }

    /**
     * 近30天报告生成趋势
     */
    @GetMapping("/trends/last30days")
    public ApiResult<List<ReportGenerateTrendVO>> last30DaysTrend() {
        return ApiResult.ok(dashboardService.last30DaysTrend());
    }
}