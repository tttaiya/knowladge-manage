package com.km.admin.review.service.impl;

import com.km.admin.review.common.PageResult;
import com.km.admin.review.dto.ApproveReviewRequest;
import com.km.admin.review.dto.RejectReviewRequest;
import com.km.admin.review.entity.ReviewRecord;
import com.km.admin.review.mapper.DocumentChunkMapper;
import com.km.admin.review.mapper.DocumentMapper;
import com.km.admin.review.mapper.ReviewRecordMapper;
import com.km.admin.review.service.ReviewService;
import com.km.admin.review.vo.PendingReviewDocumentVO;
import com.km.admin.review.vo.ReviewChunkVO;
import com.km.admin.review.vo.ReviewDocumentDetailVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ReviewRecordMapper reviewRecordMapper;

    public ReviewServiceImpl(DocumentMapper documentMapper,
                             DocumentChunkMapper documentChunkMapper,
                             ReviewRecordMapper reviewRecordMapper) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.reviewRecordMapper = reviewRecordMapper;
    }

    @Override
    public PageResult<PendingReviewDocumentVO> getPendingDocuments(Long kbId, Integer page, Integer pageSize) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int offset = (currentPage - 1) * currentPageSize;
        Long total = documentMapper.countPendingReviewDocuments(kbId);
        List<PendingReviewDocumentVO> records = documentMapper.selectPendingReviewDocuments(kbId, offset, currentPageSize);
        return PageResult.of(total, currentPage, currentPageSize, records);
    }

    @Override
    public ReviewDocumentDetailVO getReviewDocumentDetail(Long docId) {
        ReviewDocumentDetailVO detail = documentMapper.selectReviewDocumentDetail(docId);
        if (detail == null) {
            return null;
        }
        List<String> tags = documentMapper.selectDocumentTags(docId);
        List<ReviewChunkVO> chunks = documentChunkMapper.selectReviewChunksByDocId(docId);
        detail.setTags(tags);
        detail.setChunks(chunks);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approveDocument(Long docId, ApproveReviewRequest request) {
        String status = documentMapper.selectDocumentStatus(docId);
        if (status == null) {
            return 0;
        }
        if (!"PENDING_REVIEW".equals(status)) {
            return -1;
        }

        int updated = documentMapper.updateDocumentStatus(docId, "READY");
        if (updated <= 0) {
            return 0;
        }

        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setDocId(docId);
        reviewRecord.setAction("APPROVE");
        reviewRecord.setComment(request == null ? null : request.getComment());
        reviewRecord.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(reviewRecord);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rejectDocument(Long docId, RejectReviewRequest request) {
        String reason = request == null ? null : request.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            return -2;
        }

        String status = documentMapper.selectDocumentStatus(docId);
        if (status == null) {
            return 0;
        }
        if (!"PENDING_REVIEW".equals(status)) {
            return -1;
        }

        int updated = documentMapper.updateDocumentStatus(docId, "REVIEW_REJECTED");
        if (updated <= 0) {
            return 0;
        }

        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setDocId(docId);
        reviewRecord.setAction("REJECT");
        reviewRecord.setComment(reason);
        reviewRecord.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(reviewRecord);
        return updated;
    }
}

