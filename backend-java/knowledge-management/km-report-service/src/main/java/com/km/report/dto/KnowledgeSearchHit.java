package com.km.report.dto;

import lombok.Data;

@Data
public class KnowledgeSearchHit {
    private Long chunkId;
    private Long docId;
    private String docName;
    private Long kbId;
    private String kbName;
    private String chapterPath;
    private Integer pageNo;
    private String vectorId;
    private String content;
    private String summary;
    private Double similarityScore;
    private Double rerankScore;
}
