package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_template_chapter")
public class ReportTemplateChapter {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long parentId;
    private String chapterTitle;
    private String chapterNo;
    private String chapterType;
    private Integer level;
    private Integer sort;
    private String defaultContent;
    private String writingPrompt;
    private Integer requiredFlag;
    @Version
    private Integer version;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
