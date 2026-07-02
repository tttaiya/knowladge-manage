package com.km.report.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChapterTreeSaveDTO {
    private Long templateId;
    private List<ChapterNodeDTO> chapters;
}
