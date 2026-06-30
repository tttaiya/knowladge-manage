package com.km.admin.knowledgebase.service;

import com.km.admin.common.BusinessException;
import com.km.admin.knowledgebase.entity.KnowledgeBase;
import com.km.admin.knowledgebase.mapper.KnowledgeBaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库删除门面。
 * F2 v1.0 5.4 节：单删/批删走此 facade。
 *
 * <p>业务规则：
 * <ul>
 *   <li>单删：在途任务校验（2005）→ 软删除 KB → 同事务级联软删除关联 doc
 *   <li>批删：前置校验（不删除任一已删 KB / 不删除存在在途任务的 KB）→ 全部校验通过后整批软删除
 *       任一前置失败则抛 2005 / 5001，整批回滚
 *   <li>commits #29 启用：级联 km_document 软删除（is_deleted=1, deleted_at=NOW）
 * </ul>
 *
 * <p>commit #28 阶段：占位实现，仅做软删除；批删不做事务失败回滚。
 * <p>commit #29 阶段：替换为完整在途任务校验 + 事务级联。
 */
@Component
public class KnowledgeBaseDeleteFacade {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseDeleteFacade.class);

    @Autowired
    private KnowledgeBaseMapper kbMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 单个删除。
     * 业务规则：知识库存在 → 无在途任务 → 软删除 KB → 级联软删除关联 doc。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSingle(Long id, String userId) {
        KnowledgeBase existing = kbMapper.getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("知识库不存在：id=" + id);
        }
        if (Integer.valueOf(1).equals(existing.getIsDeleted())) {
            throw new IllegalArgumentException("知识库已删除：id=" + id);
        }

        // 2005：在途任务校验
        List<Long> inFlight = listInFlightDocIds(id);
        if (!inFlight.isEmpty()) {
            throw BusinessException.inFlightTask(id);
        }

        // 软删除 KB
        int rows = kbMapper.softDeleteById(id);
        if (rows == 0) {
            throw new IllegalArgumentException("知识库删除失败：id=" + id);
        }

        // 级联软删除关联 doc（不回退 doc 计数，因为 commit #29 暂未维护 document_count 触发器）
        cascadeSoftDeleteDocs(id);

        log.info("KB soft-deleted: id={} by={}", id, userId);
    }

    /**
     * 批量删除。
     * 前置：所有 KB 都存在且未删；都不存在在途任务。
     * 任一前置失败 → 5001 整批失败回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids, String userId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("knowledgeBaseIds 不能为空");
        }
        Set<Long> uniqueIds = new HashSet<>(ids);
        List<KnowledgeBase> rows = kbMapper.listByIds(new ArrayList<>(uniqueIds));

        // 1) KB 存在性校验
        if (rows.size() != uniqueIds.size()) {
            Set<Long> found = new HashSet<>();
            for (KnowledgeBase kb : rows) found.add(kb.getId());
            Set<Long> missing = new HashSet<>(uniqueIds);
            missing.removeAll(found);
            throw BusinessException.transactionFailed(
                "部分知识库不存在：" + missing);
        }

        // 2) 全部 active 校验
        for (KnowledgeBase kb : rows) {
            if (Integer.valueOf(1).equals(kb.getIsDeleted())) {
                throw BusinessException.transactionFailed(
                    "知识库已删除：id=" + kb.getId());
            }
        }

        // 3) 在途任务校验（任一存在 → 整批失败）
        for (KnowledgeBase kb : rows) {
            List<Long> inFlight = listInFlightDocIds(kb.getId());
            if (!inFlight.isEmpty()) {
                throw BusinessException.inFlightTask(kb.getId());
            }
        }

        // 4) 全部前置通过：执行整批软删除 + 级联
        for (KnowledgeBase kb : rows) {
            kbMapper.softDeleteById(kb.getId());
            cascadeSoftDeleteDocs(kb.getId());
        }
        log.info("KB batch soft-deleted: ids={} by={}", ids, userId);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 查询某 KB 下处于 QUEUED/RUNNING 的任务数。
     * 若 > 0 表示存在在途任务，2005。
     */
    private List<Long> listInFlightDocIds(Long kbId) {
        return jdbcTemplate.queryForList(
            "select distinct doc_id from km_document_process_task " +
                "where kb_id=? and task_status in ('QUEUED','RUNNING')",
            Long.class, kbId);
    }

    /**
     * 级联软删除 KB 下所有未删除 doc。
     */
    private void cascadeSoftDeleteDocs(Long kbId) {
        jdbcTemplate.update(
            "update km_document set is_deleted=1, deleted_at=now(), updated_at=now() " +
                "where kb_id=? and is_deleted=0",
            kbId);
    }
}
