package com.km.admin.review.dto;

/**
 * 审核通过请求。已去除 Lombok。
 */
public class ApproveReviewRequest {
    private String comment;

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
