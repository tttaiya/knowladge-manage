package com.km.report.dto;

import lombok.Data;

@Data
public class OutlineItemDTO {
    private Long id;
    private Long parentId;
    private String chapterNo;
    private String chapterTitle;
    private Integer level;
    private Integer sort;
    private Integer editable;
    private Integer aiGenerated;
}
