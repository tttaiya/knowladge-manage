package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_record")
public class ReportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportName;
    private String reportType;
    private Long templateId;
    private String sourceType;
    private String major;
    private String powerPlant;
    private Integer reportYear;
    private String generationPrompt;
    private Integer enableKnowledgeRetrieval;
    private Integer enableOcr;
    private Integer enableHistoryRef;
    private String sourceIds;
    private Integer status;
    private String failReason;
    private Integer totalChapter;
    private Integer finishedChapter;
    private Integer exportStatus;
    private String fileUrl;
    private String docxUrl;
    private String pdfUrl;
    private String userId;
    @Version
    private Integer version;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
