package com.km.report.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "report.storage")
public class ReportStorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String materialsBucket = "report-materials";
    private String exportsBucket = "report-exports";
}
