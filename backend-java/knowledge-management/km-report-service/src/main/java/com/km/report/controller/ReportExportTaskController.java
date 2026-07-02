package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.common.exception.BizException;
import com.km.report.entity.ReportExportTask;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportExportTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/export-tasks")
public class ReportExportTaskController {

    @Resource
    private ReportExportTaskService reportExportTaskService;
    @Resource
    private ReportAccessService reportAccessService;

    @GetMapping
    public ApiResult<List<ReportExportTask>> list(@RequestParam(value = "reportId", required = false) Long reportId) {
        return ApiResult.ok(reportExportTaskService.listTasks(reportId));
    }

    @GetMapping("/{id}")
    public ApiResult<ReportExportTask> getById(@PathVariable Long id) {
        ReportExportTask task = reportExportTaskService.getOne(new LambdaQueryWrapper<ReportExportTask>()
                .eq(ReportExportTask::getId, id)
                .eq(ReportExportTask::getCreatorId, reportAccessService.currentUserId())
                .last("LIMIT 1"));
        if (task == null) {
            throw new BizException("导出任务不存在或无权访问");
        }
        return ApiResult.ok(task);
    }
}
