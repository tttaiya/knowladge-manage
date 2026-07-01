package com.km.admin.review.mapper;

import com.km.admin.review.vo.PendingReviewDocumentVO;
import com.km.admin.review.vo.ReviewDocumentDetailVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DocumentMapper {

    List<PendingReviewDocumentVO> selectPendingReviewDocuments(@Param("kbId") Long kbId,
                                                               @Param("offset") Integer offset,
                                                               @Param("pageSize") Integer pageSize);

    Long countPendingReviewDocuments(@Param("kbId") Long kbId);

    ReviewDocumentDetailVO selectReviewDocumentDetail(@Param("docId") Long docId);

    List<String> selectDocumentTags(@Param("docId") Long docId);

    String selectDocumentStatus(@Param("docId") Long docId);

    Long countActiveChunks(@Param("docId") Long docId);

    Long countNotReadyActiveChunks(@Param("docId") Long docId);

    int updateDocumentStatus(@Param("docId") Long docId,
                             @Param("status") String status);

    int insertStatusLog(@Param("docId") Long docId,
                        @Param("stage") String stage,
                        @Param("status") String status,
                        @Param("message") String message);
}
