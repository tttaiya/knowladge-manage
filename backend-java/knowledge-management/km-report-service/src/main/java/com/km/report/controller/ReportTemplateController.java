package com.km.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.km.report.common.result.ApiResult;
import com.km.report.dto.ChapterTreeSaveDTO;
import com.km.report.dto.TemplateQueryDTO;
import com.km.report.dto.TemplateSaveDTO;
import com.km.report.service.ReportTemplateService;
import com.km.report.vo.ChapterTreeVO;
import com.km.report.vo.TemplateVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/templates")
public class ReportTemplateController {

    @Resource
    private ReportTemplateService reportTemplateService;

    @GetMapping
    public ApiResult<List<TemplateVO>> list() {
        return ApiResult.ok(reportTemplateService.listVisible(null));
    }

    @GetMapping("/page")
    public ApiResult<Page<TemplateVO>> page(TemplateQueryDTO queryDTO) {
        return ApiResult.ok(reportTemplateService.pageTemplates(queryDTO));
    }

    @GetMapping("/visible")
    public ApiResult<List<TemplateVO>> visible(String reportType) {
        return ApiResult.ok(reportTemplateService.listVisible(reportType));
    }

    @GetMapping("/{id}")
    public ApiResult<TemplateVO> getById(@PathVariable Long id) {
        return ApiResult.ok(reportTemplateService.getTemplateVO(id));
    }

    @PostMapping
    public ApiResult<Long> create(@RequestBody TemplateSaveDTO saveDTO) {
        return ApiResult.ok(reportTemplateService.addTemplate(saveDTO));
    }

    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody TemplateSaveDTO saveDTO) {
        saveDTO.setId(id);
        reportTemplateService.updateTemplate(saveDTO);
        return ApiResult.ok(true);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        reportTemplateService.deleteTemplate(id);
        return ApiResult.ok(true);
    }

    @PutMapping("/{id}/publish")
    public ApiResult<Boolean> publish(@PathVariable Long id) {
        reportTemplateService.publishTemplate(id);
        return ApiResult.ok(true);
    }

    @PutMapping("/{id}/offline")
    public ApiResult<Boolean> offline(@PathVariable Long id) {
        reportTemplateService.offlineTemplate(id);
        return ApiResult.ok(true);
    }

    @GetMapping("/{id}/chapters")
    public ApiResult<List<ChapterTreeVO>> getChapters(@PathVariable Long id) {
        return ApiResult.ok(reportTemplateService.getChapterTree(id));
    }

    @PostMapping("/chapters")
    public ApiResult<Boolean> saveChapters(@RequestBody ChapterTreeSaveDTO saveDTO) {
        reportTemplateService.saveChapterTree(saveDTO);
        return ApiResult.ok(true);
    }

    @PostMapping("/{id}/upload")
    public ApiResult<TemplateVO> uploadTemplate(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ApiResult.ok(reportTemplateService.uploadTemplateFile(id, file));
    }
}
