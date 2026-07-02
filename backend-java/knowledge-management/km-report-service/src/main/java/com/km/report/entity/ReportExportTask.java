package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_export_task")
public class ReportExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private String exportFormat;
    private Integer status;
    private String fileUrl;
    private String bucket;
    private String objectKey;
    private Long fileSize;
    private String failReason;
    private String triggerType;
    private String creatorId;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
    @TableLogic
    private Integer deleted;
}
