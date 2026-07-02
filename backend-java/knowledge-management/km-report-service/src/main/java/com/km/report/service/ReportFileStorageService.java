package com.km.report.service;

import com.km.report.vo.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface ReportFileStorageService {

    FileUploadVO store(MultipartFile file, String bizDir);

    void download(String relativePath, HttpServletResponse response);
}
