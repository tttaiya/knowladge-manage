package com.km.admin.config.dto;

/**
 * 嵌入配置 DTO（API Key 默认掩码返回，前端必须用 ****** 回传表示不修改）。
 */
public class EmbeddingConfigDTO {
    private String model;
    private String apiBase;
    private String apiKey;
    private Integer dimension;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getApiBase() { return apiBase; }
    public void setApiBase(String apiBase) { this.apiBase = apiBase; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Integer getDimension() { return dimension; }
    public void setDimension(Integer dimension) { this.dimension = dimension; }
}