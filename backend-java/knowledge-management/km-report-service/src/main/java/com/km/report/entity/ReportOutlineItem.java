package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_outline_item")
public class ReportOutlineItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long parentId;
    private String chapterTitle;
    private String chapterNo;
    private Integer level;
    private Integer sort;
    private Integer editable;
    private Integer aiGenerated;
    private String generationPrompt;
    private String status;
    @Version
    private Integer version;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
