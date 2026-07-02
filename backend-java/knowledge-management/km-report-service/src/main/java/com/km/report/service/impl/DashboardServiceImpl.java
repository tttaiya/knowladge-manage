package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.entity.ReportRecord;
import com.km.report.entity.ReportTemplate;
import com.km.report.mapper.ReportRecordMapper;
import com.km.report.service.DashboardService;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportRecordService;
import com.km.report.service.ReportTemplateService;
import com.km.report.vo.DashboardOverviewVO;
import com.km.report.vo.ReportGenerateTrendVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private ReportTemplateService reportTemplateService;

    @Resource
    private ReportRecordService reportRecordService;

    @Resource
    private ReportRecordMapper reportRecordMapper;
    @Resource
    private ReportAccessService reportAccessService;

    @Override
    public DashboardOverviewVO overview() {
        DashboardOverviewVO vo = new DashboardOverviewVO();

        String userId = reportAccessService.currentUserId();
        Long templateCount = reportTemplateService.count(new LambdaQueryWrapper<ReportTemplate>()
                .and(w -> w.eq(ReportTemplate::getTemplateScope, "GLOBAL")
                        .or()
                        .eq(ReportTemplate::getCreatorId, userId)));
        Long reportRecordCount = reportRecordService.count(new LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getUserId, userId));

        Long generatingRecordCount = reportRecordService.count(
                new LambdaQueryWrapper<ReportRecord>()
                        .eq(ReportRecord::getUserId, userId)
                        .eq(ReportRecord::getStatus, 0)
        );

        Long successRecordCount = reportRecordService.count(
                new LambdaQueryWrapper<ReportRecord>()
                        .eq(ReportRecord::getUserId, userId)
                        .eq(ReportRecord::getStatus, 1)
        );

        Long failedRecordCount = reportRecordService.count(
                new LambdaQueryWrapper<ReportRecord>()
                        .eq(ReportRecord::getUserId, userId)
                        .eq(ReportRecord::getStatus, 2)
        );

        vo.setTemplateCount(templateCount);
        vo.setReportRecordCount(reportRecordCount);
        vo.setGeneratingRecordCount(generatingRecordCount);
        vo.setSuccessRecordCount(successRecordCount);
        vo.setFailedRecordCount(failedRecordCount);
        vo.setTrendList(last30DaysTrend());

        return vo;
    }

    @Override
    public List<ReportGenerateTrendVO> last30DaysTrend() {
        List<ReportGenerateTrendVO> dbList = reportRecordMapper.selectLast30DaysTrend(reportAccessService.currentUserId());

        Map<String, Long> dbMap = new LinkedHashMap<>();
        for (ReportGenerateTrendVO item : dbList) {
            dbMap.put(item.getDate(), item.getCount());
        }

        List<ReportGenerateTrendVO> result = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(29);

        for (int i = 0; i < 30; i++) {
            LocalDate date = start.plusDays(i);
            String dateStr = date.toString();

            ReportGenerateTrendVO item = new ReportGenerateTrendVO();
            item.setDate(dateStr);
            item.setCount(dbMap.getOrDefault(dateStr, 0L));

            result.add(item);
        }

        return result;
    }
}
