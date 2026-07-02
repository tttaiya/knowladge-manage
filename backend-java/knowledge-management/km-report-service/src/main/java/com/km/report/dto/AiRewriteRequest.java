package com.km.report.dto;

import lombok.Data;

@Data
public class AiRewriteRequest {
    private Long chapterId;
    private Long reportId;
    private String chapterTitle;
    private String currentContent;
    private String chapterContext;
    private String materialContext;
    private String rewriteGoal;
    private Integer targetLength;
}
