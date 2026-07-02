package com.km.report.dto;

import lombok.Data;

@Data
public class UploadMaterialRequest {
    private String materialName;
    private String materialType;
    private String reportType;
    private String major;
    private String powerPlant;
    private Integer reportYear;
    private String remark;
}
