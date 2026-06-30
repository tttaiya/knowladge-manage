package com.km.admin.review.vo;

/**
 * 审核切片 VO。已去除 Lombok。
 */
public class ReviewChunkVO {

    private Long chunkId;
    private Integer chunkIndex;
    private String chapterPath;
    private Integer pageNo;
    private String chunkType;
    private String content;
    private Integer charCount;
    private String vectorId;
    private String vectorStatus;
    private Integer isEdited;

    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getChapterPath() { return chapterPath; }
    public void setChapterPath(String chapterPath) { this.chapterPath = chapterPath; }

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }

    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }

    public String getVectorId() { return vectorId; }
    public void setVectorId(String vectorId) { this.vectorId = vectorId; }

    public String getVectorStatus() { return vectorStatus; }
    public void setVectorStatus(String vectorStatus) { this.vectorStatus = vectorStatus; }

    public Integer getIsEdited() { return isEdited; }
    public void setIsEdited(Integer isEdited) { this.isEdited = isEdited; }
}
