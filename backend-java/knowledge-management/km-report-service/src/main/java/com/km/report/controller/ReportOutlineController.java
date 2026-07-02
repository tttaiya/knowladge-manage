package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.dto.GenerateOutlineRequest;
import com.km.report.entity.ReportOutlineItem;
import com.km.report.service.ReportOutlineService;
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
@RequestMapping("/api/v1/reports/outlines")
public class ReportOutlineController {

    @Resource
    private ReportOutlineService reportOutlineService;

    @PostMapping("/draft")
    public ApiResult<Long> createDraft(@RequestBody GenerateOutlineRequest request) {
        return ApiResult.ok(reportOutlineService.createDraft(request));
    }

    @GetMapping("/{reportId}")
    public ApiResult<List<ReportOutlineItem>> getOutline(@PathVariable Long reportId) {
        return ApiResult.ok(reportOutlineService.getOutline(reportId));
    }

    @GetMapping("/items/{itemId}")
    public ApiResult<ReportOutlineItem> getItem(@PathVariable Long itemId) {
        return ApiResult.ok(reportOutlineService.getItem(itemId));
    }

    @PostMapping("/{reportId}/items")
    public ApiResult<ReportOutlineItem> addItem(@PathVariable Long reportId, @RequestBody ReportOutlineItem item) {
        return ApiResult.ok(reportOutlineService.addItem(reportId, item));
    }

    @PostMapping("/{reportId}/regenerate")
    public ApiResult<List<ReportOutlineItem>> regenerate(@PathVariable Long reportId) {
        return ApiResult.ok(reportOutlineService.regenerateOutline(reportId));
    }

    @PutMapping("/items/{itemId}")
    public ApiResult<ReportOutlineItem> updateItem(@PathVariable Long itemId, @RequestBody ReportOutlineItem item) {
        return ApiResult.ok(reportOutlineService.updateItem(itemId, item));
    }

    @PutMapping("/{reportId}")
    public ApiResult<List<ReportOutlineItem>> update(@PathVariable Long reportId, @RequestBody List<ReportOutlineItem> items) {
        return ApiResult.ok(reportOutlineService.updateOutline(reportId, items));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResult<Boolean> deleteItem(@PathVariable Long itemId) {
        reportOutlineService.deleteOutlineItem(itemId);
        return ApiResult.ok(true);
    }

    @PostMapping("/{reportId}/items/{itemId}/move")
    public ApiResult<List<ReportOutlineItem>> moveItem(@PathVariable Long reportId,
                                                      @PathVariable Long itemId,
                                                      @RequestBody ReportOutlineItem item) {
        return ApiResult.ok(reportOutlineService.moveItem(reportId, itemId, item.getSort(), item.getParentId()));
    }
}
