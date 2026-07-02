package com.km.report.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.km.report.entity.ReportExportTask;

import java.util.List;

public interface ReportExportTaskService extends IService<ReportExportTask> {
    List<ReportExportTask> listTasks(Long reportId);
}
