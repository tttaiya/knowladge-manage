package com.km.admin.review.controller;

import com.km.admin.review.common.PageResult;
import com.km.admin.review.common.Result;
import com.km.admin.review.dto.ApproveReviewRequest;
import com.km.admin.review.dto.RejectReviewRequest;
import com.km.admin.review.dto.UpdateChunkRequest;
import com.km.admin.review.service.ChunkEditService;
import com.km.admin.review.service.ReviewService;
import com.km.admin.review.vo.PendingReviewDocumentVO;
import com.km.admin.review.vo.ReviewDocumentDetailVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final ChunkEditService chunkEditService;

    public ReviewController(ReviewService reviewService, ChunkEditService chunkEditService) {
        this.reviewService = reviewService;
        this.chunkEditService = chunkEditService;
    }

    @GetMapping("/pending-documents")
    public Result<PageResult<PendingReviewDocumentVO>> getPendingDocuments(@RequestParam(required = false) Long kbId,
                                                                          @RequestParam(defaultValue = "1") Integer page,
                                                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(reviewService.getPendingDocuments(kbId, page, pageSize));
    }

    @GetMapping("/documents/{docId}")
    public Result<ReviewDocumentDetailVO> getReviewDocumentDetail(@PathVariable Long docId) {
        ReviewDocumentDetailVO detail = reviewService.getReviewDocumentDetail(docId);
        if (detail == null) {
            return Result.fail(2003, "document not found");
        }
        return Result.success(detail);
    }

    @PatchMapping("/chunks/{chunkId}")
    public Result<Integer> updateChunk(@PathVariable Long chunkId,
                                       @RequestBody UpdateChunkRequest request,
                                       @RequestHeader(value = "X-User-Id", required = false) String operatorUserId,
                                       @RequestHeader(value = "X-User-Name", required = false) String operatorName) {
        int updated = chunkEditService.updateChunk(chunkId, request, operatorUserId, operatorName);
        if (updated == -1) {
            return Result.fail(1001, "content cannot be empty");
        }
        if (updated == -3) {
            return Result.fail(401, "missing user context");
        }
        if (updated == 0) {
            return Result.fail(2010, "chunk not found");
        }
        return Result.success(updated);
    }

    @PostMapping("/documents/{docId}/approve")
    public Result<Integer> approveDocument(@PathVariable Long docId,
                                           @RequestBody ApproveReviewRequest request,
                                           @RequestHeader(value = "X-User-Id", required = false) String operatorUserId,
                                           @RequestHeader(value = "X-User-Name", required = false) String operatorName) {
        int updated = reviewService.approveDocument(docId, request, operatorUserId, operatorName);
        if (updated == 0) {
            return Result.fail(2003, "document not found");
        }
        if (updated == -1) {
            return Result.fail(2004, "document status not allowed");
        }
        if (updated == -3) {
            return Result.fail(401, "missing user context");
        }
        if (updated == -4) {
            return Result.fail(2005, "document chunks are not ready for review approval");
        }
        return Result.success(updated);
    }

    @PostMapping("/documents/{docId}/reject")
    public Result<Integer> rejectDocument(@PathVariable Long docId,
                                          @RequestBody RejectReviewRequest request,
                                          @RequestHeader(value = "X-User-Id", required = false) String operatorUserId,
                                          @RequestHeader(value = "X-User-Name", required = false) String operatorName) {
        int updated = reviewService.rejectDocument(docId, request, operatorUserId, operatorName);
        if (updated == -2) {
            return Result.fail(1001, "reason cannot be empty");
        }
        if (updated == -3) {
            return Result.fail(401, "missing user context");
        }
        if (updated == 0) {
            return Result.fail(2003, "document not found");
        }
        if (updated == -1) {
            return Result.fail(2004, "document status not allowed");
        }
        return Result.success(updated);
    }
}
