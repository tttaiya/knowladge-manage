package com.km.admin.knowledgebase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.common.PageResult;
import com.km.admin.knowledgebase.dto.BatchDeleteKnowledgeBaseRequest;
import com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest;
import com.km.admin.knowledgebase.dto.QueryKnowledgeBaseRequest;
import com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import com.km.admin.knowledgebase.entity.KnowledgeBase;
import com.km.admin.knowledgebase.mapper.KnowledgeBaseMapper;
import com.km.admin.knowledgebase.vo.KnowledgeBaseDetailVO;
import com.km.admin.knowledgebase.vo.KnowledgeBaseSnapshotVO;
import com.km.admin.knowledgebase.vo.KnowledgeBaseVO;
import com.km.admin.knowledgebase.vo.ReprocessKnowledgeBaseResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库业务服务。
 * F2 v1.0 文档 5.3-5.5 节。
 *
 * <p>commit #28 阶段：
 * <ul>
 *   <li>create / update / list / detail / batchDelete / reprocess 占位实现
 *   <li>reprocess 仅返回 readyDocumentCount（与邱子悦原版一致），不真创建任务
 *   <li>任务/策略快照生成仍由 commit #29 启用 KnowledgeBaseTaskFacade 后生效
 * </ul>
 *
 * <p>commit #29 阶段：replace 内部方法调用 facade；service 接口签名不变。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** 业务允许的 category 取值（F2 v1.0 6.3） */
    private static final Set<String> CATEGORIES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList("REGULATION", "REPORT_PAPER", "TERM", "GENERAL")));

    @Autowired
    private KnowledgeBaseMapper kbMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /** commit #29 注入：占位为 null，由 @Autowired(required=false) 引入；commit #29 改 @Autowired */
    @Autowired(required = false)
    private KnowledgeBaseTaskFacade taskFacade;

    @Autowired(required = false)
    private KnowledgeBaseDeleteFacade deleteFacade;

    // ============================================================
    // 创建
    // ============================================================

    public KnowledgeBaseVO create(CreateKnowledgeBaseRequest req, String userId, String userName) {
        validateCategory(req.getCategory());
        validateStrategyFields(req.getRetrievalStrategy(), req.getChunkStrategy(),
            req.getChunkSize(), req.getChunkOverlap(), req.getSeparatorsJson());

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(req.getName().trim());
        kb.setDescription(req.getDescription());
        kb.setCategory(req.getCategory());
        kb.setRetrievalStrategy(req.getRetrievalStrategy());
        kb.setChunkStrategy(defaultIfBlank(req.getChunkStrategy(), "HEADING"));
        kb.setChunkSize(req.getChunkSize() == null ? 500 : req.getChunkSize());
        kb.setChunkOverlap(req.getChunkOverlap() == null ? 50 : req.getChunkOverlap());
        kb.setSeparatorsJson(normalizeSeparatorsJson(req.getSeparatorsJson()));
        kb.setDocumentCount(0);
        kb.setCreatedByUserId(userId);
        kb.setCreatedByName(userName);
        kb.setStrategyVersion(1L);

        try {
            kbMapper.insert(kb);
        } catch (Exception e) {
            // 唯一索引冲突 uk_kb_active_name → 1001
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("uk_kb_active_name")) {
                throw new IllegalArgumentException("知识库名称已存在（仅未删除知识库要求唯一）");
            }
            throw e;
        }
        log.info("KB created: id={}, name={}, by={}", kb.getId(), kb.getName(), userId);
        return toListVO(kb);
    }

    // ============================================================
    // 更新
    // ============================================================

    /**
     * @param confirmation 策略变更显式确认；非策略字段变更可传 null
     */
    @Transactional
    public KnowledgeBaseVO update(UpdateKnowledgeBaseRequest req, String userId, String userName,
                                  Boolean confirmation) {
        KnowledgeBase existing = kbMapper.getById(req.getId());
        if (existing == null) {
            throw new IllegalArgumentException("知识库不存在：id=" + req.getId());
        }
        if (Integer.valueOf(1).equals(existing.getIsDeleted())) {
            throw new IllegalArgumentException("已删除知识库不可更新：id=" + req.getId());
        }

        boolean strategyChanged = isStrategyChanged(existing, req);

        if (strategyChanged) {
            if (confirmation == null || !confirmation) {
                throw new IllegalStateException(
                    "策略变更需要显式确认（confirmation=true），当前操作被拒绝");
            }
            validateStrategyFields(req.getRetrievalStrategy() != null ? req.getRetrievalStrategy() : existing.getRetrievalStrategy(),
                req.getChunkStrategy() != null ? req.getChunkStrategy() : existing.getChunkStrategy(),
                req.getChunkSize() != null ? req.getChunkSize() : existing.getChunkSize(),
                req.getChunkOverlap() != null ? req.getChunkOverlap() : existing.getChunkOverlap(),
                req.getSeparatorsJson() != null ? req.getSeparatorsJson() : existing.getSeparatorsJson());
        }

        if (req.getName() != null && !req.getName().isEmpty()) {
            existing.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            existing.setDescription(req.getDescription());
        }
        if (req.getCategory() != null) {
            validateCategory(req.getCategory());
            existing.setCategory(req.getCategory());
        }
        if (req.getRetrievalStrategy() != null) existing.setRetrievalStrategy(req.getRetrievalStrategy());
        if (req.getChunkStrategy() != null) existing.setChunkStrategy(req.getChunkStrategy());
        if (req.getChunkSize() != null) existing.setChunkSize(req.getChunkSize());
        if (req.getChunkOverlap() != null) existing.setChunkOverlap(req.getChunkOverlap());
        if (req.getSeparatorsJson() != null) existing.setSeparatorsJson(normalizeSeparatorsJson(req.getSeparatorsJson()));

        // 策略变更：strategy_version 单调递增；非策略变更：保持原值
        if (strategyChanged) {
            existing.setStrategyVersion(
                existing.getStrategyVersion() == null ? 2L : existing.getStrategyVersion() + 1L);
        }

        try {
            kbMapper.updateById(existing);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("uk_kb_active_name")) {
                throw new IllegalArgumentException("知识库名称已存在（仅未删除知识库要求唯一）");
            }
            throw e;
        }
        log.info("KB updated: id={}, name={}, strategyChanged={}, by={}",
            existing.getId(), existing.getName(), strategyChanged, userId);
        return toListVO(existing);
    }

    // ============================================================
    // 查询
    // ============================================================

    public PageResult<KnowledgeBaseVO> list(QueryKnowledgeBaseRequest req) {
        int pageNum = Math.max(1, req.getPageNum());
        int pageSize = Math.max(1, Math.min(100, req.getPageSize()));
        int offset = (pageNum - 1) * pageSize;
        int total = kbMapper.countByQuery(req.getCategory(), req.getNameKeyword(), req.getIsDeleted());
        List<KnowledgeBase> rows = total == 0 ? new ArrayList<KnowledgeBase>()
            : kbMapper.listByQuery(req.getCategory(), req.getNameKeyword(), req.getIsDeleted(), offset, pageSize);
        List<KnowledgeBaseVO> vos = new ArrayList<KnowledgeBaseVO>(rows.size());
        for (KnowledgeBase kb : rows) {
            vos.add(toListVO(kb));
        }
        return new PageResult<KnowledgeBaseVO>(vos, total, pageNum, pageSize);
    }

    public KnowledgeBaseDetailVO detail(Long id) {
        KnowledgeBase kb = kbMapper.getById(id);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在：id=" + id);
        }
        return toDetailVO(kb);
    }

    // ============================================================
    // 删除
    // ============================================================

    @Transactional
    public void delete(Long id, String userId) {
        if (deleteFacade != null) {
            deleteFacade.deleteSingle(id, userId);
        } else {
            // commit #28 兜底：仅做软删除，不做在途任务校验，不级联文档
            // 真实逻辑在 commit #29 由 facade 接管
            log.warn("KnowledgeBaseDeleteFacade 未注入，仅做软删除（commit #28 兜底）");
            int rows = kbMapper.softDeleteById(id);
            if (rows == 0) {
                throw new IllegalArgumentException("知识库不存在或已删除：id=" + id);
            }
        }
    }

    @Transactional
    public void batchDelete(BatchDeleteKnowledgeBaseRequest req, String userId) {
        if (deleteFacade != null) {
            deleteFacade.deleteBatch(req.getKnowledgeBaseIds(), userId);
        } else {
            // commit #28 兜底
            log.warn("KnowledgeBaseDeleteFacade 未注入，仅做软删除（commit #28 兜底）");
            for (Long id : req.getKnowledgeBaseIds()) {
                kbMapper.softDeleteById(id);
            }
        }
    }

    // ============================================================
    // 策略变更（reprocess）
    // ============================================================

    public ReprocessKnowledgeBaseResultVO reprocess(Long id, String userId, String userName) {
        KnowledgeBase kb = kbMapper.getById(id);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在：id=" + id);
        }
        if (Integer.valueOf(1).equals(kb.getIsDeleted())) {
            throw new IllegalArgumentException("已删除知识库不可触发策略变更：id=" + id);
        }

        // 真实任务创建（commit #29 启用 facade；commit #28 占位）
        Long taskId = null;
        if (taskFacade != null) {
            taskId = taskFacade.createStrategyReprocessTask(kb, userId, userName);
        } else {
            log.warn("KnowledgeBaseTaskFacade 未注入，reprocess 仅返回 readyDocumentCount（commit #28 占位）");
        }

        ReprocessKnowledgeBaseResultVO vo = new ReprocessKnowledgeBaseResultVO();
        vo.setKnowledgeBaseId(id);
        vo.setTaskId(taskId);
        vo.setReadyDocumentCount(kb.getDocumentCount() == null ? 0 : kb.getDocumentCount());
        vo.setMessage(taskId == null
            ? "已记录策略变更请求（commit #28 占位，未创建真实任务）"
            : "策略变更任务已创建，请前往任务中心查看进度");
        vo.setTriggeredAt(LocalDateTime.now());
        return vo;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private boolean isStrategyChanged(KnowledgeBase existing, UpdateKnowledgeBaseRequest req) {
        if (req.getRetrievalStrategy() != null && !req.getRetrievalStrategy().equals(existing.getRetrievalStrategy())) return true;
        if (req.getChunkStrategy() != null && !req.getChunkStrategy().equals(existing.getChunkStrategy())) return true;
        if (req.getChunkSize() != null && !req.getChunkSize().equals(existing.getChunkSize())) return true;
        if (req.getChunkOverlap() != null && !req.getChunkOverlap().equals(existing.getChunkOverlap())) return true;
        if (req.getSeparatorsJson() != null && !req.getSeparatorsJson().equals(existing.getSeparatorsJson())) return true;
        return false;
    }

    private void validateCategory(String category) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new IllegalArgumentException(
                "category 取值非法：" + category + "，允许值 " + CATEGORIES);
        }
    }

    private void validateStrategyFields(String retrieval, String chunkStrategy,
                                        Integer chunkSize, Integer chunkOverlap, String separatorsJson) {
        if (retrieval == null || !(retrieval.equals("VECTOR_RERANK") || retrieval.equals("SEMANTIC"))) {
            throw new IllegalArgumentException("retrievalStrategy 必须是 VECTOR_RERANK 或 SEMANTIC");
        }
        if (chunkStrategy == null || !(chunkStrategy.equals("HEADING") || chunkStrategy.equals("FIXED"))) {
            throw new IllegalArgumentException("chunkStrategy 必须是 HEADING 或 FIXED");
        }
        if (chunkSize == null || chunkSize < 50) {
            throw new IllegalArgumentException("chunkSize 必须 ≥ 50");
        }
        if (chunkOverlap == null || chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap 必须 ≥ 0");
        }
        if ("FIXED".equals(chunkStrategy) && chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("FIXED 策略下 chunkOverlap 必须小于 chunkSize");
        }
        // separatorsJson 格式校验
        if (separatorsJson != null && !separatorsJson.isEmpty()) {
            try {
                List<String> seps = objectMapper.readValue(separatorsJson,
                    new TypeReference<List<String>>() {});
                if (seps == null || seps.isEmpty()) {
                    throw new IllegalArgumentException("separatorsJson 不能为空数组");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("separatorsJson 解析失败：" + e.getMessage());
            }
        }
    }

    private String normalizeSeparatorsJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "[]";
        }
        try {
            List<String> seps = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return objectMapper.writeValueAsString(seps);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String defaultIfBlank(String s, String dft) {
        return (s == null || s.isEmpty()) ? dft : s;
    }

    private KnowledgeBaseVO toListVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(kb, vo);
        return vo;
    }

    private KnowledgeBaseDetailVO toDetailVO(KnowledgeBase kb) {
        KnowledgeBaseDetailVO vo = new KnowledgeBaseDetailVO();
        BeanUtils.copyProperties(kb, vo);
        if (kb.getSeparatorsJson() != null && !kb.getSeparatorsJson().isEmpty()) {
            try {
                vo.setSeparators(objectMapper.readValue(kb.getSeparatorsJson(),
                    new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                vo.setSeparators(Collections.<String>emptyList());
            }
        } else {
            vo.setSeparators(Collections.<String>emptyList());
        }
        return vo;
    }

    /** commit #29 由 KnowledgeBaseTaskFacade 反射调用：构建策略快照。 */
    public KnowledgeBaseSnapshotVO toSnapshot(KnowledgeBase kb) {
        KnowledgeBaseSnapshotVO snap = new KnowledgeBaseSnapshotVO();
        snap.setId(kb.getId());
        snap.setName(kb.getName());
        snap.setRetrievalStrategy(kb.getRetrievalStrategy());
        snap.setChunkStrategy(kb.getChunkStrategy());
        snap.setChunkSize(kb.getChunkSize());
        snap.setChunkOverlap(kb.getChunkOverlap());
        if (kb.getSeparatorsJson() != null && !kb.getSeparatorsJson().isEmpty()) {
            try {
                snap.setSeparators(objectMapper.readValue(kb.getSeparatorsJson(),
                    new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                snap.setSeparators(Collections.<String>emptyList());
            }
        }
        snap.setCapturedAt(LocalDateTime.now());
        return snap;
    }
}
