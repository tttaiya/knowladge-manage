package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_material")
public class ReportMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialName;
    private String materialType;
    private String reportType;
    private String major;
    private String powerPlant;
    private Integer reportYear;
    private String originalFileName;
    private String fileUrl;
    private String filePath;
    private String bucket;
    private String objectKey;
    private String fileExt;
    private Long fileSize;
    private String parseStatus;
    private String structuredData;
    private String creatorId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
