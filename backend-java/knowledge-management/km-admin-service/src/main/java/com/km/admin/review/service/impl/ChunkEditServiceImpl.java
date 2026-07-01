package com.km.admin.review.service.impl;

import com.km.admin.ChunkReembedProducer;
import com.km.admin.review.dto.UpdateChunkRequest;
import com.km.admin.review.entity.ChunkEditLog;
import com.km.admin.review.mapper.ChunkEditLogMapper;
import com.km.admin.review.mapper.DocumentChunkMapper;
import com.km.admin.review.service.ChunkEditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Clob;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ChunkEditServiceImpl implements ChunkEditService {

    private final DocumentChunkMapper documentChunkMapper;
    private final ChunkEditLogMapper chunkEditLogMapper;
    private final ChunkReembedProducer chunkReembedProducer;

    public ChunkEditServiceImpl(
            DocumentChunkMapper documentChunkMapper,
            ChunkEditLogMapper chunkEditLogMapper,
            ChunkReembedProducer chunkReembedProducer
    ) {
        this.documentChunkMapper = documentChunkMapper;
        this.chunkEditLogMapper = chunkEditLogMapper;
        this.chunkReembedProducer = chunkReembedProducer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateChunk(Long chunkId, UpdateChunkRequest request, String operatorUserId, String operatorName) {
        if (operatorUserId == null || operatorUserId.trim().isEmpty()) {
            return -3;
        }

        String content = request == null ? null : request.getContent();
        if (content == null || content.trim().isEmpty()) {
            return -1;
        }

        Map<String, Object> beforeChunk =
                documentChunkMapper.selectChunkForEdit(chunkId);

        if (beforeChunk == null) {
            return 0;
        }

        String beforeContent =
                toStringValue(
                        getMapValue(beforeChunk, "content")
                );

        int updated =
                documentChunkMapper.updateChunkContent(
                        chunkId,
                        content,
                        content.length()
                );

        if (updated <= 0) {
            return 0;
        }

        ChunkEditLog editLog = new ChunkEditLog();
        editLog.setChunkId(chunkId);
        editLog.setBeforeContent(beforeContent);
        editLog.setAfterContent(content);
        editLog.setAction("EDIT");
        editLog.setOperatorUserId(operatorUserId);
        editLog.setOperatorName(operatorName);
        editLog.setCreatedAt(LocalDateTime.now());
        chunkEditLogMapper.insert(editLog);

        /*
         * 重新查询更新后的切片，取得递增后的 contentVersion。
         */
        Map<String, Object> updatedChunk =
                documentChunkMapper.selectChunkForEdit(chunkId);

        if (updatedChunk == null) {
            throw new IllegalStateException(
                    "切片更新成功，但无法读取更新后的切片：" + chunkId
            );
        }

        Long docId =
                toLong(
                        getMapValue(updatedChunk, "docId")
                );

        Long kbId =
                toLong(
                        getMapValue(updatedChunk, "kbId")
                );

        Long contentVersion =
                toLong(
                        getMapValue(
                                updatedChunk,
                                "contentVersion"
                        )
                );

        if (docId == null || contentVersion == null) {
            throw new IllegalStateException(
                    "切片缺少 REEMBED 必需字段：chunkId="
                            + chunkId
            );
        }

        chunkReembedProducer.send(
                chunkId,
                docId,
                kbId,
                operatorUserId,
                contentVersion
        );

        return updated;
    }

    private Object getMapValue(
            Map<String, Object> map,
            String key
    ) {
        if (map == null || key == null) {
            return null;
        }

        if (map.containsKey(key)) {
            return map.get(key);
        }

        if (map.containsKey(key.toLowerCase())) {
            return map.get(key.toLowerCase());
        }

        if (map.containsKey(key.toUpperCase())) {
            return map.get(key.toUpperCase());
        }

        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return Long.valueOf(value.toString());
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            return (String) value;
        }

        if (value instanceof Clob) {
            try {
                Clob clob = (Clob) value;
                return clob.getSubString(
                        1,
                        (int) clob.length()
                );
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "Failed to read chunk content",
                        e
                );
            }
        }

        return value.toString();
    }
}
