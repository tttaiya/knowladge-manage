package com.km.report.service;

import com.km.report.vo.DashboardOverviewVO;
import com.km.report.vo.ReportGenerateTrendVO;

import java.util.List;

public interface DashboardService {

    /**
     * 管理后台概览
     */
    DashboardOverviewVO overview();

    /**
     * 近30天报告生成趋势
     */
    List<ReportGenerateTrendVO> last30DaysTrend();
}