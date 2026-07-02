package com.km.admin.review.service.impl;

import com.km.admin.DocumentTaskFacade;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final DocumentTaskFacade documentTaskFacade;
    private final JdbcTemplate jdbcTemplate;

    public ReviewServiceImpl(DocumentMapper documentMapper,
                             DocumentChunkMapper documentChunkMapper,
                             ReviewRecordMapper reviewRecordMapper,
                             DocumentTaskFacade documentTaskFacade,
                             JdbcTemplate jdbcTemplate) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.documentTaskFacade = documentTaskFacade;
        this.jdbcTemplate = jdbcTemplate;
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
    public int approveDocument(Long docId, ApproveReviewRequest request, String operatorUserId, String operatorName) {
        if (isBlank(operatorUserId)) {
            return -3;
        }

        String status = documentMapper.selectDocumentStatus(docId);
        if (status == null) {
            return 0;
        }
        if (!"PENDING_REVIEW".equals(status)) {
            Long candidateVersion = selectPendingCandidateVersion(docId);
            if (candidateVersion == null) {
                return -1;
            }
            return approveCandidateVersion(docId, candidateVersion, request, operatorUserId, operatorName);
        }
        Long activeChunks = documentMapper.countActiveChunks(docId);
        Long notReadyChunks = documentMapper.countNotReadyActiveChunks(docId);
        if (activeChunks == null || activeChunks <= 0 || (notReadyChunks != null && notReadyChunks > 0)) {
            return -4;
        }

        int updated = documentMapper.updateDocumentStatus(docId, "READY");
        if (updated <= 0) {
            return 0;
        }

        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setDocId(docId);
        reviewRecord.setAction("APPROVE");
        reviewRecord.setComment(request == null ? null : request.getComment());
        reviewRecord.setOperatorUserId(operatorUserId);
        reviewRecord.setOperatorName(operatorName);
        reviewRecord.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(reviewRecord);
        documentMapper.insertStatusLog(docId, "REVIEW", "READY", "approved by " + displayName(operatorName, operatorUserId));
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rejectDocument(Long docId, RejectReviewRequest request, String operatorUserId, String operatorName) {
        if (isBlank(operatorUserId)) {
            return -3;
        }

        String reason = request == null ? null : request.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            return -2;
        }

        String status = documentMapper.selectDocumentStatus(docId);
        if (status == null) {
            return 0;
        }
        if (!"PENDING_REVIEW".equals(status)) {
            Long candidateVersion = selectPendingCandidateVersion(docId);
            if (candidateVersion == null) {
                return -1;
            }
            return rejectCandidateVersion(docId, candidateVersion, reason, operatorUserId, operatorName);
        }

        int updated = documentMapper.updateDocumentStatus(docId, "REVIEW_REJECTED");
        if (updated <= 0) {
            return 0;
        }

        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setDocId(docId);
        reviewRecord.setAction("REJECT");
        reviewRecord.setComment(reason);
        reviewRecord.setOperatorUserId(operatorUserId);
        reviewRecord.setOperatorName(operatorName);
        reviewRecord.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(reviewRecord);
        documentMapper.insertStatusLog(docId, "REVIEW", "REVIEW_REJECTED", reason.trim());
        documentTaskFacade.createReviewRejectedReprocessTask(docId, reviewRecord.getId(), operatorUserId);
        return updated;
    }

    private Long selectPendingCandidateVersion(Long docId) {
        List<Long> versions = jdbcTemplate.queryForList(
                "select version_no from km_document_version " +
                        "where doc_id=? and version_status='PENDING_REVIEW' order by version_no desc limit 1",
                Long.class, docId);
        return versions.isEmpty() ? null : versions.get(0);
    }

    private int approveCandidateVersion(Long docId, Long candidateVersion,
                                        ApproveReviewRequest request,
                                        String operatorUserId, String operatorName) {
        Long chunkCount = jdbcTemplate.queryForObject(
                "select count(1) from km_document_chunk where doc_id=? and version_no=?",
                Long.class, docId, candidateVersion);
        Long notReadyChunks = jdbcTemplate.queryForObject(
                "select count(1) from km_document_chunk where doc_id=? and version_no=? and coalesce(vector_status, '') != 'READY'",
                Long.class, docId, candidateVersion);
        if (chunkCount == null || chunkCount <= 0 || (notReadyChunks != null && notReadyChunks > 0)) {
            return -4;
        }

        List<Long> currentVersions = jdbcTemplate.queryForList(
                "select current_version_no from km_document where id=? and is_deleted=0 for update",
                Long.class, docId);
        if (currentVersions.isEmpty()) {
            return 0;
        }
        Long previousVersion = currentVersions.get(0);
        jdbcTemplate.update("update km_document_chunk set is_active=0 where doc_id=? and version_no=?",
                docId, previousVersion);
        jdbcTemplate.update("update km_document_chunk set is_active=1 where doc_id=? and version_no=?",
                docId, candidateVersion);
        jdbcTemplate.update("update km_document_version set version_status='RETIRED' where doc_id=? and version_status='ACTIVE'",
                docId);
        jdbcTemplate.update("update km_document_version set version_status='ACTIVE', activated_at=now() where doc_id=? and version_no=?",
                docId, candidateVersion);
        jdbcTemplate.update("update km_document set current_version_no=?, document_status='READY', updated_at=now() where id=?",
                candidateVersion, docId);
        jdbcTemplate.update("insert into km_vector_cleanup_task(doc_id, keep_version_no, cleanup_status, created_at) values(?,?,'PENDING',now())",
                docId, candidateVersion);

        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setDocId(docId);
        reviewRecord.setAction("APPROVE");
        reviewRecord.setComment(request == null ? null : request.getComment());
        reviewRecord.setOperatorUserId(operatorUserId);
        reviewRecord.setOperatorName(operatorName);
        reviewRecord.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(reviewRecord);
        documentMapper.insertStatusLog(docId, "REVIEW", "READY", "candidate version " + candidateVersion
                + " approved by " + displayName(operatorName, operatorUserId));
        return 1;
    }

    private int rejectCandidateVersion(Long docId, Long candidateVersion, String reason,
                                       String operatorUserId, String operatorName) {
        int updated = jdbcTemplate.update(
                "update km_document_version set version_status='REJECTED' where doc_id=? and version_no=? and version_status='PENDING_REVIEW'",
                docId, candidateVersion);
        if (updated <= 0) {
            return 0;
        }
        jdbcTemplate.update("update km_document_chunk set is_active=0 where doc_id=? and version_no=?",
                docId, candidateVersion);
        jdbcTemplate.update("update km_document set document_status='READY', updated_at=now() where id=?",
                docId);
        jdbcTemplate.update("insert into km_vector_cleanup_task(doc_id, keep_version_no, cleanup_status, created_at) " +
                        "select id, current_version_no, 'PENDING', now() from km_document where id=?",
                docId);

        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setDocId(docId);
        reviewRecord.setAction("REJECT");
        reviewRecord.setComment(reason);
        reviewRecord.setOperatorUserId(operatorUserId);
        reviewRecord.setOperatorName(operatorName);
        reviewRecord.setCreatedAt(LocalDateTime.now());
        reviewRecordMapper.insert(reviewRecord);
        documentMapper.insertStatusLog(docId, "REVIEW", "REJECTED", reason.trim());
        return 1;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String displayName(String operatorName, String operatorUserId) {
        return isBlank(operatorName) ? operatorUserId : operatorName;
    }
}
