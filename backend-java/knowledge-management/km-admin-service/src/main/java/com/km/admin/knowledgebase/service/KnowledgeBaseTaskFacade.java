package com.km.admin.knowledgebase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.DocumentTaskFacade;
import com.km.admin.knowledgebase.entity.KnowledgeBase;
import com.km.admin.knowledgebase.vo.KnowledgeBaseSnapshotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KnowledgeBaseTaskFacade {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseTaskFacade.class);

    private static final String STRATEGY_TRIGGER_SOURCE = "KB_STRATEGY_CHANGED";
    private static final String MANUAL_TRIGGER_SOURCE = "KB_MANUAL_REPROCESS";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentTaskFacade documentTaskFacade;

    public KnowledgeBaseTaskFacade(JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper,
                                   DocumentTaskFacade documentTaskFacade) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.documentTaskFacade = documentTaskFacade;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createStrategyReprocessTask(KnowledgeBase kb, String userId, String userName) {
        return createReprocessTaskBatch(kb, userId, userName, STRATEGY_TRIGGER_SOURCE, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createManualReprocessTask(KnowledgeBase kb, String userId, String userName) {
        return createReprocessTaskBatch(kb, userId, userName, MANUAL_TRIGGER_SOURCE, true);
    }

    public int countStrategyReprocessTasks(Long kbId, Long strategyVersion) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from km_document_process_task " +
                        "where kb_id=? and task_type='REPROCESS' and trigger_source=? and strategy_version=?",
                Integer.class, kbId, STRATEGY_TRIGGER_SOURCE, strategyVersion);
        return count == null ? 0 : count;
    }

    public int countReprocessableDocuments(Long kbId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from km_document where kb_id=? and is_deleted=0 and document_status='READY'",
                Integer.class, kbId);
        return count == null ? 0 : count;
    }

    protected Long createReprocessTaskBatch(KnowledgeBase kb, String userId, String userName,
                                            String triggerSource, boolean useManualBatchKey) {
        List<Long> docIds = listReadyDocumentIds(kb.getId());
        if (docIds.isEmpty()) {
            log.info("KB {} has no READY documents, skip REPROCESS task creation", kb.getId());
            return null;
        }

        KnowledgeBaseSnapshotVO snapshot = buildSnapshot(kb);
        String batchToken = useManualBatchKey ? UUID.randomUUID().toString().replace("-", "") : null;
        Long primaryTaskId = null;
        for (Long docId : docIds) {
            String idempotencyKey = buildIdempotencyKey(
                    kb.getId(), docId, kb.getStrategyVersion(), triggerSource, batchToken);
            Map<String, Object> payload = buildPayload(docId, kb, snapshot, userId, userName, triggerSource);
            Long taskId = documentTaskFacade.createKnowledgeBaseReprocessTask(
                    docId, kb.getStrategyVersion(), userId, triggerSource, idempotencyKey, payload);
            if (primaryTaskId == null) {
                primaryTaskId = taskId;
            }
        }
        log.info("KB REPROCESS tasks created: kbId={}, strategyVersion={}, docCount={}, primaryTaskId={}, by={}",
                kb.getId(), kb.getStrategyVersion(), docIds.size(), primaryTaskId, userId);
        return primaryTaskId;
    }

    private List<Long> listReadyDocumentIds(Long kbId) {
        return jdbcTemplate.queryForList(
                "select id from km_document where kb_id=? and is_deleted=0 and document_status='READY' order by id",
                Long.class, kbId);
    }

    private String buildIdempotencyKey(Long kbId, Long docId, Long strategyVersion,
                                       String triggerSource, String batchToken) {
        if (MANUAL_TRIGGER_SOURCE.equals(triggerSource)) {
            return "KB_MANUAL_REPROCESS:" + kbId + ":" + batchToken + ":" + docId;
        }
        return "KB_REPROCESS:" + kbId + ":" + docId + ":" + strategyVersion;
    }

    private Map<String, Object> buildPayload(Long docId, KnowledgeBase kb, KnowledgeBaseSnapshotVO snapshot,
                                             String userId, String userName, String triggerSource) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("operation", "REPROCESS");
        payload.put("docId", docId);
        payload.put("kbId", kb.getId());
        payload.put("strategyVersion", kb.getStrategyVersion());
        payload.put("chunkMode", mapChunkMode(kb.getChunkStrategy()));
        payload.put("chunkSize", kb.getChunkSize());
        payload.put("overlap", kb.getChunkOverlap());
        payload.put("separator", snapshot.getSeparators());
        payload.put("operatorUserId", userId);
        payload.put("operatorUserName", userName);
        payload.put("triggerReason", MANUAL_TRIGGER_SOURCE.equals(triggerSource)
                ? "USER_MANUAL_REPROCESS" : "USER_STRATEGY_CHANGE");
        payload.put("knowledgeBaseSnapshot", objectMapper.convertValue(
                snapshot, new TypeReference<Map<String, Object>>() { }));
        return payload;
    }

    private KnowledgeBaseSnapshotVO buildSnapshot(KnowledgeBase kb) {
        KnowledgeBaseSnapshotVO snap = new KnowledgeBaseSnapshotVO();
        snap.setId(kb.getId());
        snap.setName(kb.getName());
        snap.setRetrievalStrategy(kb.getRetrievalStrategy());
        snap.setChunkStrategy(kb.getChunkStrategy());
        snap.setChunkSize(kb.getChunkSize());
        snap.setChunkOverlap(kb.getChunkOverlap());
        snap.setSeparators(readSeparators(kb.getSeparatorsJson()));
        snap.setCapturedAt(LocalDateTime.now());
        return snap;
    }

    private List<String> readSeparators(String separatorsJson) {
        if (separatorsJson == null || separatorsJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(separatorsJson, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return new ArrayList<String>();
        }
    }

    private String mapChunkMode(String chunkStrategy) {
        return "HEADING".equalsIgnoreCase(chunkStrategy) ? "heading" : "fixed";
    }
}
