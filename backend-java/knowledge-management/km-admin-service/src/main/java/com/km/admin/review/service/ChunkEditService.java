package com.km.admin.review.service;

import com.km.admin.review.dto.UpdateChunkRequest;

public interface ChunkEditService {

    int updateChunk(Long chunkId, UpdateChunkRequest request);
}

