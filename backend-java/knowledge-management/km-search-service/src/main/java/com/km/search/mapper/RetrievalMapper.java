package com.km.search.mapper;

import com.km.search.dto.ChunkDetailRecord;
import com.km.search.dto.DocTagRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RetrievalMapper {

    @Select({
            "<script>",
            "SELECT id, retrieval_strategy AS retrievalStrategy",
            "FROM km_knowledge_base",
            "WHERE is_deleted = 0",
            "  AND id IN",
            "  <foreach collection='knowledgeBaseIds' item='kbId' open='(' separator=',' close=')'>",
            "    #{kbId}",
            "  </foreach>",
            "</script>"
    })
    List<Map<String, Object>> selectKnowledgeBasePolicies(
            @Param("knowledgeBaseIds")
            List<Long> knowledgeBaseIds
    );

    @Select({
            "<script>",
            "SELECT d.id",
            "FROM km_document d",
            "<if test='tags != null and tags.size() &gt; 0'>",
            "JOIN km_document_tag t ON t.doc_id = d.id",
            "</if>",
            "WHERE d.document_status = 'READY'",
            "  AND d.is_deleted = 0",
            "<if test='knowledgeBaseIds != null and knowledgeBaseIds.size() &gt; 0'>",
            "  AND d.kb_id IN",
            "  <foreach collection='knowledgeBaseIds' item='kbId' open='(' separator=',' close=')'>",
            "    #{kbId}",
            "  </foreach>",
            "</if>",
            "<if test='tags != null and tags.size() &gt; 0'>",
            "  AND t.tag_name IN",
            "  <foreach collection='tags' item='tag' open='(' separator=',' close=')'>",
            "    #{tag}",
            "  </foreach>",
            "GROUP BY d.id",
            "HAVING COUNT(DISTINCT t.tag_name) = #{tagCount}",
            "</if>",
            "</script>"
    })
    List<Long> selectReadyDocIds(
            @Param("knowledgeBaseIds")
            List<Long> knowledgeBaseIds,

            @Param("tags")
            List<String> tags,

            @Param("tagCount")
            int tagCount
    );

    /**
     * 保留旧的按 chunkId 批量查询方法，
     * 避免影响当前项目中的其他调用方。
     */
    @Select({
            "<script>",
            "SELECT",
            "  c.id AS chunkId,",
            "  c.doc_id AS docId,",
            "  d.kb_id AS kbId,",
            "  d.file_name AS docName,",
            "  kb.name AS kbName,",
            "  c.chapter_path AS chapterPath,",
            "  c.page_no AS pageNo,",
            "  c.chunk_type AS chunkType,",
            "  c.content AS content,",
            "  c.vector_id AS vectorId",
            "FROM km_document_chunk c",
            "JOIN km_document d ON d.id = c.doc_id",
            "JOIN km_knowledge_base kb ON kb.id = d.kb_id",
            "WHERE c.id IN",
            "<foreach collection='chunkIds' item='chunkId' open='(' separator=',' close=')'>",
            "  #{chunkId}",
            "</foreach>",
            "  AND c.is_active = 1",
            "  AND c.vector_status = 'READY'",
            "  AND d.document_status = 'READY'",
            "  AND d.is_deleted = 0",
            "  AND kb.is_deleted = 0",
            "</script>"
    })
    List<ChunkDetailRecord> selectChunkDetailsByIds(
            @Param("chunkIds")
            List<Long> chunkIds
    );

    /**
     * 根据 FastAPI 返回的 vectorId 批量回查真实切片。
     *
     * ChromaDB 只保存 vectorId 和向量；
     * chunkId、正文、页码、标题路径等业务数据以 MySQL 为准。
     */
    @Select({
            "<script>",
            "SELECT",
            "  c.id AS chunkId,",
            "  c.doc_id AS docId,",
            "  d.kb_id AS kbId,",
            "  d.file_name AS docName,",
            "  kb.name AS kbName,",
            "  c.chapter_path AS chapterPath,",
            "  c.page_no AS pageNo,",
            "  c.chunk_type AS chunkType,",
            "  c.content AS content,",
            "  c.vector_id AS vectorId",
            "FROM km_document_chunk c",
            "JOIN km_document d ON d.id = c.doc_id",
            "JOIN km_knowledge_base kb ON kb.id = d.kb_id",
            "WHERE c.vector_id IN",
            "<foreach collection='vectorIds' item='vectorId' open='(' separator=',' close=')'>",
            "  #{vectorId}",
            "</foreach>",
            "  AND c.is_active = 1",
            "  AND c.vector_status = 'READY'",
            "  AND d.document_status = 'READY'",
            "  AND d.is_deleted = 0",
            "  AND kb.is_deleted = 0",
            "</script>"
    })
    List<ChunkDetailRecord> selectChunkDetailsByVectorIds(
            @Param("vectorIds")
            List<String> vectorIds
    );

    @Select({
            "<script>",
            "SELECT",
            "  dt.doc_id AS docId,",
            "  dt.tag_name AS tagName",
            "FROM km_document_tag dt",
            "WHERE dt.doc_id IN",
            "<foreach collection='docIds' item='docId' open='(' separator=',' close=')'>",
            "  #{docId}",
            "</foreach>",
            "ORDER BY dt.doc_id ASC, dt.tag_name ASC",
            "</script>"
    })
    List<DocTagRow> selectTagsByDocIds(
            @Param("docIds")
            List<Long> docIds
    );

    @Select({
            "SELECT",
            "  c.id AS chunkId,",
            "  c.doc_id AS docId,",
            "  d.kb_id AS kbId,",
            "  d.file_name AS docName,",
            "  kb.name AS kbName,",
            "  c.chapter_path AS chapterPath,",
            "  c.page_no AS pageNo,",
            "  c.chunk_type AS chunkType,",
            "  c.content AS content,",
            "  c.vector_id AS vectorId",
            "FROM km_document_chunk c",
            "JOIN km_document d ON d.id = c.doc_id",
            "JOIN km_knowledge_base kb ON kb.id = d.kb_id",
            "WHERE c.id = #{chunkId}",
            "  AND c.is_active = 1",
            "  AND c.vector_status = 'READY'",
            "  AND d.document_status = 'READY'",
            "  AND d.is_deleted = 0",
            "  AND kb.is_deleted = 0"
    })
    ChunkDetailRecord selectChunkDetail(
            @Param("chunkId")
            Long chunkId
    );
}
