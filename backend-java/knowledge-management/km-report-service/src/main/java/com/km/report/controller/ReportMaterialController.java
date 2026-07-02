package com.km.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.km.report.common.result.ApiResult;
import com.km.report.config.ReportExportProperties;
import com.km.report.dto.MaterialQueryDTO;
import com.km.report.dto.UploadMaterialRequest;
import com.km.report.entity.ReportMaterial;
import com.km.report.service.ReportFileStorageService;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportMaterialService;
import com.km.report.vo.FileUploadVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/reports/materials")
public class ReportMaterialController {

    @Resource
    private ReportMaterialService reportMaterialService;

    @Resource
    private ReportFileStorageService reportFileStorageService;

    @Resource
    private ReportExportProperties reportExportProperties;
    @Resource
    private ReportAccessService reportAccessService;

    @GetMapping("/page")
    public ApiResult<Page<ReportMaterial>> page(MaterialQueryDTO queryDTO) {
        return ApiResult.ok(reportMaterialService.pageMaterials(queryDTO));
    }

    @GetMapping("/{id}")
    public ApiResult<ReportMaterial> getById(@PathVariable Long id) {
        return ApiResult.ok(reportAccessService.requireOwnedMaterial(id));
    }

    @PostMapping("/upload")
    public ApiResult<ReportMaterial> upload(@RequestParam("file") MultipartFile file, @ModelAttribute UploadMaterialRequest request) {
        return ApiResult.ok(reportMaterialService.uploadMaterial(file, request));
    }

    @PostMapping("/{id}/parse")
    public ApiResult<ReportMaterial> parse(@PathVariable Long id) {
        reportAccessService.requireOwnedMaterial(id);
        return ApiResult.ok(reportMaterialService.parseMaterial(id));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        reportAccessService.requireOwnedMaterial(id);
        return ApiResult.ok(reportMaterialService.removeById(id));
    }

    @PostMapping("/images")
    public ApiResult<FileUploadVO> uploadImage(@RequestParam("file") MultipartFile file) {
        reportAccessService.currentUserId();
        return ApiResult.ok(reportFileStorageService.store(file, reportExportProperties.getImageDir()));
    }

    @GetMapping("/files/**")
    public void download(javax.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        String prefix = "/api/v1/reports/materials/files/";
        String uri = request.getRequestURI();
        reportFileStorageService.download(uri.substring(uri.indexOf(prefix) + prefix.length()), response);
    }
}
