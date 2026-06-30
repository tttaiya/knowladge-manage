package com.km.admin.review.mapper;

import com.km.admin.review.vo.ReviewChunkVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface DocumentChunkMapper {

    List<ReviewChunkVO> selectReviewChunksByDocId(@Param("docId") Long docId);

    Map<String, Object> selectChunkForEdit(@Param("chunkId") Long chunkId);

    int updateChunkContent(@Param("chunkId") Long chunkId,
                           @Param("content") String content,
                           @Param("charCount") Integer charCount);
}

