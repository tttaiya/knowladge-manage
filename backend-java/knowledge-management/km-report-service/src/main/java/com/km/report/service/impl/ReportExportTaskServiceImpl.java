package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.entity.ReportExportTask;
import com.km.report.mapper.ReportExportTaskMapper;
import com.km.report.service.ReportExportTaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ReportExportTaskServiceImpl extends ServiceImpl<ReportExportTaskMapper, ReportExportTask> implements ReportExportTaskService {

    @Override
    public List<ReportExportTask> listTasks(Long reportId) {
        LambdaQueryWrapper<ReportExportTask> queryWrapper = new LambdaQueryWrapper<ReportExportTask>()
                .orderByDesc(ReportExportTask::getCreateTime);
        if (reportId != null) {
            queryWrapper.eq(ReportExportTask::getReportId, reportId);
        }
        return this.list(queryWrapper);
    }
}
