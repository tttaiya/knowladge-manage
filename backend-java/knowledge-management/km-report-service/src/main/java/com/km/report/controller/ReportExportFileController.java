package com.km.report.controller;

import com.km.report.config.ReportExportProperties;
import com.km.report.service.ReportFileStorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/reports/files")
public class ReportExportFileController {

    @Resource
    private ReportFileStorageService reportFileStorageService;

    @Resource
    private ReportExportProperties reportExportProperties;

    @GetMapping("/**")
    public void download(HttpServletRequest request, HttpServletResponse response) {
        String prefix = reportExportProperties.getUrlPrefix() + "/";
        String uri = request.getRequestURI();
        reportFileStorageService.download(uri.substring(uri.indexOf(prefix) + prefix.length()), response);
    }
}