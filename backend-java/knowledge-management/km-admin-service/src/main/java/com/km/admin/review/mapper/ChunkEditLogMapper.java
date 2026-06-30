package com.km.admin.review.mapper;

import com.km.admin.review.entity.ChunkEditLog;

import java.util.List;

public interface ChunkEditLogMapper {

    List<ChunkEditLog> selectByChunkId(Long chunkId);

    int insert(ChunkEditLog chunkEditLog);
}

