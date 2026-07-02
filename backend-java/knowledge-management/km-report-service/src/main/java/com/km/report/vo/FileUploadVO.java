package com.km.report.vo;

import lombok.Data;

@Data
public class FileUploadVO {
    private String originalFileName;
    private String fileName;
    private String fileUrl;
    private String filePath;
    private String bucket;
    private String objectKey;
    private String fileExt;
    private Long fileSize;
}
