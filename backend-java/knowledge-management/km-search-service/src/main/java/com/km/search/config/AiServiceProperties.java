package com.km.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "km.ai")
public class AiServiceProperties {

    private String baseUrl;
    private String retrievalPath = "/internal/ai/retrieval/search";
    private String internalToken;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRetrievalPath() {
        return retrievalPath;
    }

    public void setRetrievalPath(String retrievalPath) {
        this.retrievalPath = retrievalPath;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}

