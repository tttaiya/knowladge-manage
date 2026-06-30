package com.km.admin.review.dto;

/**
 * 审核拒绝请求。已去除 Lombok。
 */
public class RejectReviewRequest {
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
