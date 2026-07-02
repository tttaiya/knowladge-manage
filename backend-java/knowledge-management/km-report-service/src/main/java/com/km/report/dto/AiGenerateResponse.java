package com.km.report.dto;

import lombok.Data;

@Data
public class AiGenerateResponse {
    private String content;
    private String rawResponse;
    private String finishReason;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
