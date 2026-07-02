package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.dto.ReportPageQueryDTO;
import com.km.report.entity.ReportRecord;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportRecordService;
import com.km.report.vo.PageResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/records")
public class ReportRecordController {

    @Resource
    private ReportRecordService reportRecordService;
    @Resource
    private ReportAccessService reportAccessService;

    @GetMapping
    public ApiResult<List<ReportRecord>> list() {
        return ApiResult.ok(reportRecordService.listOwned());
    }

    @GetMapping("/page")
    public ApiResult<PageResultVO<ReportRecord>> page(ReportPageQueryDTO queryDTO) {
        return ApiResult.ok(reportRecordService.pageRecords(queryDTO));
    }

    @GetMapping("/{id}")
    public ApiResult<ReportRecord> getById(@PathVariable Long id) {
        return ApiResult.ok(reportAccessService.requireOwnedRecord(id));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        reportAccessService.requireOwnedRecord(id);
        return ApiResult.ok(reportRecordService.removeById(id));
    }
}
