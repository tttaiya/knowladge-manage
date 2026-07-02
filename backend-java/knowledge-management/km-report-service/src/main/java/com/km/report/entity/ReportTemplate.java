package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_template")
public class ReportTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String reportType;
    private String description;
    private Integer status;
    private String templateScope;
    private Integer chapterCount;
    private String styleConfig;
    private String originalFileName;
    private String fileUrl;
    private Long fileSize;
    private String creatorId;
    @Version
    private Integer version;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
