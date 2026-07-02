package com.km.report.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "report.export")
public class ReportExportProperties {

    /**
     * 文件访问前缀
     */
    private String urlPrefix;

    /**
     * 模板上传子目录
     */
    private String templateDir = "templates";

    /**
     * 素材上传子目录
     */
    private String materialDir = "materials";

    /**
     * 图片上传子目录
     */
    private String imageDir = "images";
}
