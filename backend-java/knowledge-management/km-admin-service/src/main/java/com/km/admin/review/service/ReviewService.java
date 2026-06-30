package com.km.admin.review.service;

import com.km.admin.review.common.PageResult;
import com.km.admin.review.dto.ApproveReviewRequest;
import com.km.admin.review.dto.RejectReviewRequest;
import com.km.admin.review.vo.PendingReviewDocumentVO;
import com.km.admin.review.vo.ReviewDocumentDetailVO;

public interface ReviewService {

    PageResult<PendingReviewDocumentVO> getPendingDocuments(Long kbId, Integer page, Integer pageSize);

    ReviewDocumentDetailVO getReviewDocumentDetail(Long docId);

    int approveDocument(Long docId, ApproveReviewRequest request);

    int rejectDocument(Long docId, RejectReviewRequest request);
}

