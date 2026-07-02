package com.km.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report_ai_call_log")
public class ReportAiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long chapterId;
    private String callType;
    private String modelName;
    private String requestId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long durationMs;
    private Integer status;
    private String errorMsg;
    private String requestBody;
    private String responseBody;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
