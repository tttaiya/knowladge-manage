package com.km.report.dto;

import lombok.Data;

@Data
public class UpdateOutlineItemRequest {
    private Long parentId;
    private String chapterNo;
    private String chapterTitle;
    private Integer level;
    private Integer sort;
}
