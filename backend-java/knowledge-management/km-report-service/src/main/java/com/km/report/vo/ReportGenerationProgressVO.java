package com.km.report.vo;

import lombok.Data;

@Data
public class ReportGenerationProgressVO {
    private Long reportId;
    private Integer totalChapter;
    private Integer finishedChapter;
    private Integer status;
    private String currentChapterTitle;
    private Long currentChapterId;
    private String currentContent;
    private String message;
}
