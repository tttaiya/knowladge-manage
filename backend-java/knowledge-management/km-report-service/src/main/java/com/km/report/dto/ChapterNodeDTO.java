package com.km.report.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChapterNodeDTO {
    private Long id;
    private String chapterTitle;
    private String chapterType;
    private Integer level;
    private Integer sort;
    private String defaultContent;
    private String writingPrompt;
    private List<ChapterNodeDTO> children;
}
