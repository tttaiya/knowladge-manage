package com.km.admin.config.dto;

/**
 * 重排序配置 DTO（API Key 默认掩码返回）。
 */
public class RerankConfigDTO {
    private String model;
    private String apiBase;
    private String apiKey;
    private Integer topN;
    private Double threshold;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getApiBase() { return apiBase; }
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Integer getTopN() { return topN; }
    public void setTopN(Integer topN) { this.topN = topN; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
}