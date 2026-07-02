package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.entity.ReportExportTask;
import com.km.report.mapper.ReportExportTaskMapper;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportExportTaskService;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportExportTaskServiceImpl extends ServiceImpl<ReportExportTaskMapper, ReportExportTask> implements ReportExportTaskService {

    @Resource
    private ReportAccessService reportAccessService;

    @Override
    public List<ReportExportTask> listTasks(Long reportId) {
        LambdaQueryWrapper<ReportExportTask> queryWrapper = new LambdaQueryWrapper<ReportExportTask>()
                .eq(ReportExportTask::getCreatorId, reportAccessService.currentUserId())
                .orderByDesc(ReportExportTask::getCreateTime);
        if (reportId != null) {
            reportAccessService.requireOwnedRecord(reportId);
            queryWrapper.eq(ReportExportTask::getReportId, reportId);
        }
        return this.list(queryWrapper);
    }
}
