package com.km.admin.review.dto;

/**
 * 切片编辑请求。已去除 Lombok。
 */
public class UpdateChunkRequest {
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
