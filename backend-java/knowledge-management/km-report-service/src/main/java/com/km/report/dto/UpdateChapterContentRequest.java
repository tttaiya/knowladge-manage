package com.km.report.dto;

import lombok.Data;

@Data
public class UpdateChapterContentRequest {
    private String content;
    private String contentFormat;
    private String remark;
}
