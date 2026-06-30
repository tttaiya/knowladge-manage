package com.km.search.service;

import com.km.search.dto.ChunkDetailResponse;
import com.km.search.dto.RetrievalSearchRequest;
import com.km.search.dto.RetrievalSearchResponse;

public interface RetrievalService {

    RetrievalSearchResponse search(RetrievalSearchRequest request);

    ChunkDetailResponse getChunkDetail(Long chunkId);
}

