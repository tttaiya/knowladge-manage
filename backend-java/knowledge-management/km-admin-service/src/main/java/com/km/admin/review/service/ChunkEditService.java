package com.km.admin.review.service;

import com.km.admin.review.dto.UpdateChunkRequest;

public interface ChunkEditService {

    int updateChunk(Long chunkId, UpdateChunkRequest request, String operatorUserId, String operatorName);

    int mergeWithNext(Long chunkId, String operatorUserId, String operatorName);

    int splitChunk(Long chunkId, Integer splitAt, String operatorUserId, String operatorName);

    int deleteChunk(Long chunkId, String operatorUserId, String operatorName);
}
