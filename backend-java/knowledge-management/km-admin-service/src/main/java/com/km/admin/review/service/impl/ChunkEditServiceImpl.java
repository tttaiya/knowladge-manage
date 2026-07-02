package com.km.admin.review.service.impl;

import com.km.admin.ChunkReembedProducer;
import com.km.admin.review.dto.UpdateChunkRequest;
import com.km.admin.review.entity.ChunkEditLog;
import com.km.admin.review.mapper.ChunkEditLogMapper;
import com.km.admin.review.mapper.DocumentChunkMapper;
import com.km.admin.review.service.ChunkEditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ChunkEditServiceImpl implements ChunkEditService {

    private final DocumentChunkMapper documentChunkMapper;
    private final ChunkEditLogMapper chunkEditLogMapper;
    private final ChunkReembedProducer chunkReembedProducer;
    private final JdbcTemplate jdbcTemplate;

    public ChunkEditServiceImpl(
            DocumentChunkMapper documentChunkMapper,
            ChunkEditLogMapper chunkEditLogMapper,
            ChunkReembedProducer chunkReembedProducer,
            JdbcTemplate jdbcTemplate
    ) {
        this.documentChunkMapper = documentChunkMapper;
        this.chunkEditLogMapper = chunkEditLogMapper;
        this.chunkReembedProducer = chunkReembedProducer;
        this.jdbcTemplate = jdbcTemplate;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int mergeWithNext(Long chunkId, String operatorUserId, String operatorName) {
        if (isBlank(operatorUserId)) {
            return -3;
        }
        Map<String, Object> current = documentChunkMapper.selectChunkForEdit(chunkId);
        if (current == null) {
            return 0;
        }
        Map<String, Object> next = selectNextChunk(current);
        if (next == null) {
            return -4;
        }

        Long docId = toLong(getMapValue(current, "docId"));
        Long versionNo = toLong(getMapValue(current, "versionNo"));
        Integer currentIndex = toInteger(getMapValue(current, "chunkIndex"));
        Integer nextIndex = toInteger(getMapValue(next, "chunkIndex"));
        String beforeContent = toStringValue(getMapValue(current, "content"));
        String nextContent = toStringValue(getMapValue(next, "content"));
        String mergedContent = joinForMerge(beforeContent, nextContent);

        int updated = jdbcTemplate.update(
                "update km_document_chunk set content=?, char_count=?, content_version=content_version+1, " +
                        "is_edited=1, vector_status='PENDING', updated_at=now() where id=?",
                mergedContent, mergedContent.length(), chunkId);
        if (updated <= 0) {
            return 0;
        }
        jdbcTemplate.update("delete from km_document_chunk where id=?", toLong(getMapValue(next, "chunkId")));
        jdbcTemplate.update(
                "update km_document_chunk set chunk_index=chunk_index-1, updated_at=now() " +
                        "where doc_id=? and version_no=? and chunk_index>?",
                docId, versionNo, nextIndex);
        insertEditLog(chunkId, beforeContent, mergedContent, "MERGE", operatorUserId, operatorName);
        reembedUpdatedChunk(chunkId, operatorUserId);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int splitChunk(Long chunkId, Integer splitAt, String operatorUserId, String operatorName) {
        if (isBlank(operatorUserId)) {
            return -3;
        }
        Map<String, Object> chunk = documentChunkMapper.selectChunkForEdit(chunkId);
        if (chunk == null) {
            return 0;
        }
        String content = toStringValue(getMapValue(chunk, "content"));
        if (content == null || splitAt == null || splitAt <= 0 || splitAt >= content.length()) {
            return -1;
        }

        String left = content.substring(0, splitAt).trim();
        String right = content.substring(splitAt).trim();
        if (left.isEmpty() || right.isEmpty()) {
            return -1;
        }

        Long docId = toLong(getMapValue(chunk, "docId"));
        Long versionNo = toLong(getMapValue(chunk, "versionNo"));
        Integer chunkIndex = toInteger(getMapValue(chunk, "chunkIndex"));
        jdbcTemplate.update(
                "update km_document_chunk set chunk_index=chunk_index+1, updated_at=now() " +
                        "where doc_id=? and version_no=? and chunk_index>?",
                docId, versionNo, chunkIndex);
        jdbcTemplate.update(
                "update km_document_chunk set content=?, char_count=?, content_version=content_version+1, " +
                        "is_edited=1, vector_status='PENDING', updated_at=now() where id=?",
                left, left.length(), chunkId);
        Long newChunkId = insertSplitChunk(chunk, right, chunkIndex + 1);
        insertEditLog(chunkId, content, left + "\n--- split ---\n" + right, "SPLIT", operatorUserId, operatorName);
        reembedUpdatedChunk(chunkId, operatorUserId);
        reembedUpdatedChunk(newChunkId, operatorUserId);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteChunk(Long chunkId, String operatorUserId, String operatorName) {
        if (isBlank(operatorUserId)) {
            return -3;
        }
        Map<String, Object> chunk = documentChunkMapper.selectChunkForEdit(chunkId);
        if (chunk == null) {
            return 0;
        }
        Long docId = toLong(getMapValue(chunk, "docId"));
        Long versionNo = toLong(getMapValue(chunk, "versionNo"));
        Integer chunkIndex = toInteger(getMapValue(chunk, "chunkIndex"));
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from km_document_chunk where doc_id=? and version_no=?",
                Integer.class, docId, versionNo);
        if (count == null || count <= 1) {
            return -5;
        }
        String beforeContent = toStringValue(getMapValue(chunk, "content"));
        int deleted = jdbcTemplate.update("delete from km_document_chunk where id=?", chunkId);
        if (deleted <= 0) {
            return 0;
        }
        jdbcTemplate.update(
                "update km_document_chunk set chunk_index=chunk_index-1, updated_at=now() " +
                        "where doc_id=? and version_no=? and chunk_index>?",
                docId, versionNo, chunkIndex);
        insertEditLog(chunkId, beforeContent, "", "DELETE", operatorUserId, operatorName);
        return 1;
    }

    private Map<String, Object> selectNextChunk(Map<String, Object> current) {
        Long docId = toLong(getMapValue(current, "docId"));
        Long versionNo = toLong(getMapValue(current, "versionNo"));
        Integer chunkIndex = toInteger(getMapValue(current, "chunkIndex"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select c.id as chunkId, c.doc_id as docId, d.kb_id as kbId, c.version_no as versionNo, " +
                        "c.chunk_index as chunkIndex, c.content, c.content_version as contentVersion, " +
                        "c.chapter_path as chapterPath, c.page_no as pageNo, c.chunk_type as chunkType, " +
                        "c.char_count as charCount, c.vector_id as vectorId, c.vector_status as vectorStatus " +
                        "from km_document_chunk c join km_document d on d.id=c.doc_id " +
                        "where c.doc_id=? and c.version_no=? and c.chunk_index>? and d.is_deleted=0 " +
                        "order by c.chunk_index asc limit 1 for update",
                docId, versionNo, chunkIndex);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long insertSplitChunk(Map<String, Object> source, String content, Integer chunkIndex) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into km_document_chunk(doc_id, version_no, chunk_index, content, chapter_path, page_no, " +
                            "chunk_type, char_count, vector_status, is_active, is_edited, created_at, updated_at) " +
                            "values(?,?,?,?,?,?,?,?, 'PENDING', ?, 1, now(), now())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, toLong(getMapValue(source, "docId")));
            ps.setLong(2, toLong(getMapValue(source, "versionNo")));
            ps.setInt(3, chunkIndex);
            ps.setString(4, content);
            ps.setString(5, toStringValue(getMapValue(source, "chapterPath")));
            Object pageNo = getMapValue(source, "pageNo");
            if (pageNo == null) {
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setInt(6, toInteger(pageNo));
            }
            ps.setString(7, toStringValue(getMapValue(source, "chunkType")));
            ps.setInt(8, content.length());
            ps.setInt(9, isActive(source));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void reembedUpdatedChunk(Long chunkId, String operatorUserId) {
        Map<String, Object> updatedChunk = documentChunkMapper.selectChunkForEdit(chunkId);
        if (updatedChunk == null) {
            throw new IllegalStateException("无法读取更新后的切片：" + chunkId);
        }
        Long docId = toLong(getMapValue(updatedChunk, "docId"));
        Long kbId = toLong(getMapValue(updatedChunk, "kbId"));
        Long contentVersion = toLong(getMapValue(updatedChunk, "contentVersion"));
        if (docId == null || contentVersion == null) {
            throw new IllegalStateException("切片缺少 REEMBED 必需字段：chunkId=" + chunkId);
        }
        chunkReembedProducer.send(chunkId, docId, kbId, operatorUserId, contentVersion);
    }

    private void insertEditLog(Long chunkId, String beforeContent, String afterContent, String action,
                               String operatorUserId, String operatorName) {
        ChunkEditLog editLog = new ChunkEditLog();
        editLog.setChunkId(chunkId);
        editLog.setBeforeContent(beforeContent);
        editLog.setAfterContent(afterContent);
        editLog.setAction(action);
        editLog.setOperatorUserId(operatorUserId);
        editLog.setOperatorName(operatorName);
        editLog.setCreatedAt(LocalDateTime.now());
        chunkEditLogMapper.insert(editLog);
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

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private int isActive(Map<String, Object> chunk) {
        Object value = getMapValue(chunk, "isActive");
        if (value == null) {
            return 1;
        }
        Integer active = toInteger(value);
        return active == null ? 1 : active;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String joinForMerge(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + "\n\n" + right;
    }
}
