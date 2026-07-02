package com.km.report.dto;

import lombok.Data;

@Data
public class AiGenerateRequest {
    private String systemPrompt;
    private String userPrompt;
    private Boolean stream;
    private String responseFormat;
    private Long reportId;
    private Long chapterId;
    private String traceId;
}
