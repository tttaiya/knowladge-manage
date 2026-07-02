package com.km.report.dto;

import lombok.Data;

@Data
public class AiConfigVO {
    private Boolean enabled;
    private String baseUrl;
    private String model;
    private String apiKey;
    private String apiKeyMasked;
}
