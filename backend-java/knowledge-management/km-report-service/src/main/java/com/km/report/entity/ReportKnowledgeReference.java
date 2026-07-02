package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("report_knowledge_reference")
public class ReportKnowledgeReference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long chapterId;
    private Long knowledgeBaseId;
    private Long documentId;
    private Long chunkId;
    private String vectorId;
    private BigDecimal retrievalScore;
    private String sourceTitle;
    private String excerptSnapshot;
    private Integer sourceOrder;
    private LocalDateTime createTime;
}
