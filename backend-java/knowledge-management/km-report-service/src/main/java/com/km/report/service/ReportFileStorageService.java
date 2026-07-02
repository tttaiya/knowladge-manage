package com.km.report.service;

import com.km.report.vo.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;

public interface ReportFileStorageService {

    FileUploadVO store(MultipartFile file, String bizDir);

    FileUploadVO storeBytes(byte[] content, String originalFileName, String contentType, String bucket, String bizDir);

    InputStream open(String bucket, String objectKey);

    void download(String relativePath, HttpServletResponse response);
}
