package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.entity.ReportSystemConfig;
import com.km.report.service.ReportSystemConfigService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/configs")
public class ReportSystemConfigController {

    @Resource
    private ReportSystemConfigService reportSystemConfigService;

    @GetMapping
    public ApiResult<List<ReportSystemConfig>> list() {
        return ApiResult.ok(reportSystemConfigService.list());
    }

    @GetMapping("/{id}")
    public ApiResult<ReportSystemConfig> getById(@PathVariable Long id) {
        return ApiResult.ok(reportSystemConfigService.getById(id));
    }

    @PostMapping
    public ApiResult<Boolean> create(@RequestBody ReportSystemConfig reportSystemConfig) {
        return ApiResult.ok(reportSystemConfigService.save(reportSystemConfig));
    }

    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody ReportSystemConfig reportSystemConfig) {
        reportSystemConfig.setId(id);
        return ApiResult.ok(reportSystemConfigService.updateById(reportSystemConfig));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        return ApiResult.ok(reportSystemConfigService.removeById(id));
    }
}
