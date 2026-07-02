package com.km.report.dto;

import lombok.Data;

@Data
public class MaterialQueryDTO {
    private String keyword;
    private String materialType;
    private String reportType;
    private String major;
    private String powerPlant;
    private Integer reportYear;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
