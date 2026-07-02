package com.km.report.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChapterTreeVO {
    private Long id;
    private Long parentId;
    private String chapterTitle;
    private String chapterType;
    private Integer level;
    private Integer sort;
    private String chapterNo;
    private String defaultContent;
    private String writingPrompt;
    private List<ChapterTreeVO> children;
}
