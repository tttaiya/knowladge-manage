package com.km.admin.knowledgebase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.knowledgebase.entity.KnowledgeBase;
import com.km.admin.knowledgebase.vo.KnowledgeBaseSnapshotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 知识库策略变更门面（真实实现）。
 * F2 v1.0 5.5 节。
 *
 * <p>策略变更（reprocess）是 KB 粒度，但底层任务表 {@code km_document_process_task} 是 doc 粒度。
 * 本 facade 在事务内做 KB → 多 doc 的展开：
 * <ol>
 *   <li>查 {@code km_document} 中 {@code kb_id=? AND is_deleted=0} 的所有 docId
 *   <li>对每个 docId 写一条 REPROCESS 任务到 {@code km_document_process_task}
 *   <li>事务提交后逐条发 RabbitMQ（事务后置 hook）
 * </ol>
 *
 * <p>设计取舍（与现有 TaskCommandService 的关系）：
 * <ul>
 *   <li>TaskCommandService 是包内可见 class，跨包不可注入；本 facade 自管 jdbc + publish
 *   <li>不复用 user_quota（KB 策略变更是 admin 操作，配置级无单用户配额）
 *   <li>复用 idempotency_key UNIQUE 索引实现重入保护（同一 doc 同一 strategyVersion 只一条 task）
 *   <li>triggerSource = 'KB_STRATEGY_CHANGE'，与已有的 'STRATEGY_CHANGE'（per-doc）区分
 *   <li>routingKey 沿用 'km.doc.reprocess'（worker 端不需要区分）
 * </ul>
 */
@Component
public class KnowledgeBaseTaskFacade {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseTaskFacade.class);

    private static final String TASK_TYPE = "REPROCESS";
    private static final String TRIGGER_SOURCE = "KB_STRATEGY_CHANGE";
    private static final String EXCHANGE = "km.exchange";
    private static final String ROUTING_KEY = "km.doc.reprocess";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private KnowledgeBaseService kbService;

    @Value("${km.task.ack-timeout-seconds:10}")
    private int ackTimeoutSeconds;

    /**
     * 创建 KB 策略变更（reprocess）任务。
     * 整 KB 一次性展开为 N 条 doc-level REPROCESS 任务。
     *
     * @param kb       当前最新策略的 KB 实体（已 update 完毕）
     * @param userId   操作人 UUID
     * @param userName 操作人姓名
     * @return 主任务 ID（取第一条 doc 的 taskId 作为代表）；空 KB（无 doc）返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createStrategyReprocessTask(KnowledgeBase kb, String userId, String userName) {
        // 1) 展开 docId 列表
        List<Long> docIds = jdbcTemplate.queryForList(
            "select id from km_document where kb_id=? and is_deleted=0 order by id",
            Long.class, kb.getId());
        if (docIds.isEmpty()) {
            log.info("KB {} has no documents; skip REPROCESS task creation", kb.getId());
            return null;
        }

        // 2) 策略快照
        KnowledgeBaseSnapshotVO snapshot = kbService.toSnapshot(kb);

        // 3) 写任务并 publish
        Long primaryTaskId = null;
        for (Long docId : docIds) {
            String idempotencyKey = "KB_REPROCESS:" + kb.getId() + ":" + kb.getStrategyVersion() + ":" + docId;
            Long existingTaskId = findByIdempotencyKey(idempotencyKey);
            if (existingTaskId != null) {
                if (primaryTaskId == null) primaryTaskId = existingTaskId;
                log.info("REPROCESS task already exists: docId={} kbId={} strategyVersion={} taskId={}",
                    docId, kb.getId(), kb.getStrategyVersion(), existingTaskId);
                continue;
            }

            String traceId = UUID.randomUUID().toString().replace("-", "");
            String payloadJson = buildPayloadJson(docId, kb, snapshot, userId, userName, traceId);

            // 3.1) 抢版本号（select for update + update next_version_no）
            Long targetVersionNo = lockAndAllocateVersion(docId);

            // 3.2) jdbc insert km_document_process_task
            final String fPayloadJson = payloadJson;
            final String fTraceId = traceId;
            final String fIdemKey = idempotencyKey;
            final Long fTargetVersionNo = targetVersionNo;
            org.springframework.jdbc.support.KeyHolder keyHolder =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "insert into km_document_process_task(doc_id,kb_id,user_id,task_type,trigger_source,task_status,progress,trace_id,task_payload_json," +
                        "idempotency_key,strategy_version,target_version_no,retry_count,dispatch_status,created_at,updated_at) " +
                        "values(?,?,?,?,?,'QUEUED',0,?,?,?,?,?,?,'PENDING',now(),now())",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, docId);
                ps.setLong(2, kb.getId());
                if (userId == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, userId);
                }
                ps.setString(4, TASK_TYPE);
                ps.setString(5, TRIGGER_SOURCE);
                ps.setString(6, fTraceId);
                ps.setString(7, fPayloadJson);
                ps.setString(8, fIdemKey);
                ps.setObject(9, kb.getStrategyVersion());
                ps.setObject(10, fTargetVersionNo);
                ps.setInt(11, 0);
                return ps;
            }, keyHolder);
            Long taskId = Objects.requireNonNull(keyHolder.getKey()).longValue();
            if (primaryTaskId == null) primaryTaskId = taskId;

            // 3.3) 写 km_document_version（带 taskId，status=BUILDING）
            insertDocumentVersion(docId, targetVersionNo, kb.getStrategyVersion(), taskId);

            // 3.4) 事务后置 publish
            final Long fTaskId = taskId;
            final String fDocIdStr = String.valueOf(docId);
            final String fKbIdStr = String.valueOf(kb.getId());
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publish(fTaskId, fTargetVersionNo, fDocIdStr, fKbIdStr, TASK_TYPE, TRIGGER_SOURCE, fTraceId, fPayloadJson);
                    }
                });
        }
        log.info("KB strategy reprocess tasks created: kbId={} strategyVersion={} docCount={} primaryTaskId={} by={}",
            kb.getId(), kb.getStrategyVersion(), docIds.size(), primaryTaskId, userId);
        return primaryTaskId;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private String buildPayloadJson(Long docId, KnowledgeBase kb, KnowledgeBaseSnapshotVO snapshot,
                                    String userId, String userName, String traceId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("docId", docId);
            payload.put("kbId", kb.getId());
            payload.put("strategyVersion", kb.getStrategyVersion());
            payload.put("triggerReason", "USER_STRATEGY_CHANGE");
            payload.put("operatorUserId", userId);
            payload.put("operatorUserName", userName);
            payload.put("knowledgeBaseSnapshot", objectMapper.convertValue(snapshot,
                new TypeReference<Map<String, Object>>() {}));
            payload.put("traceId", traceId);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("构造策略变更 task_payload_json 失败：" + e.getMessage(), e);
        }
    }

    private Long findByIdempotencyKey(String key) {
        List<Long> rows = jdbcTemplate.queryForList(
            "select id from km_document_process_task where idempotency_key=?", Long.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 行锁 km_document.next_version_no 并返回当前可用版本号（同时 +1 自增）。
     * 拆分自 TaskCommandTxService.allocateBuildingVersion 第一段。
     */
    private Long lockAndAllocateVersion(Long docId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select next_version_no from km_document where id=? for update", docId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("文档不存在：" + docId);
        }
        long next = ((Number) rows.get(0).get("next_version_no")).longValue();
        jdbcTemplate.update("update km_document set next_version_no=? where id=?", next + 1, docId);
        return next;
    }

    /**
     * 写 km_document_version 行（status='BUILDING'）。
     * 拆分自 TaskCommandTxService.allocateBuildingVersion 第二段，需 taskId 必须在调用前已知。
     */
    private void insertDocumentVersion(Long docId, Long versionNo, Long strategyVersion, Long taskId) {
        jdbcTemplate.update(
            "insert into km_document_version(doc_id,version_no,strategy_version,task_id,version_status,created_at) " +
                "values(?,?,?,?,'BUILDING',now())",
            docId, versionNo, strategyVersion, taskId);
    }

    private void publish(Long taskId, Long targetVersionNo, String docId, String kbId, String taskType,
                         String triggerSource, String traceId, String payloadJson) {
        int claimed = jdbcTemplate.update(
            "update km_document_process_task set dispatch_status='DISPATCHING', dispatch_attempts=dispatch_attempts+1, " +
                "last_dispatch_attempt_at=now(), updated_at=now() where id=? and task_status='QUEUED' and dispatch_status='PENDING'",
            taskId);
        if (claimed == 0) {
            log.warn("Cannot claim task {} for publish (status changed)", taskId);
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("taskId", taskId);
            body.put("docId", docId);
            body.put("kbId", kbId);
            body.put("targetVersionNo", targetVersionNo);
            body.put("taskType", taskType);
            body.put("triggerSource", triggerSource);
            body.put("traceId", traceId);
            body.put("taskPayloadJson", parseJson(payloadJson));

            CorrelationData cd = new CorrelationData("kb-reprocess-" + taskId + "-" + System.currentTimeMillis());
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, objectMapper.writeValueAsString(body), cd);
            CorrelationData.Confirm confirm = cd.getFuture().get(ackTimeoutSeconds, TimeUnit.SECONDS);
            if (confirm != null && confirm.isAck()) {
                jdbcTemplate.update(
                    "update km_document_process_task set dispatch_status='PUBLISHED', published_at=now(), updated_at=now() " +
                        "where id=? and task_status='QUEUED'",
                    taskId);
            } else {
                throw new IllegalStateException(confirm == null ? "RabbitMQ confirm timeout" : confirm.getReason());
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (msg.length() > 900) msg = msg.substring(0, 900);
            jdbcTemplate.update(
                "update km_document_process_task set dispatch_status='PENDING', error_stage='DISPATCH', error_message=?, updated_at=now() " +
                    "where id=? and task_status='QUEUED' and dispatch_status='DISPATCHING'",
                msg, taskId);
            log.error("Publish KB strategy reprocess task failed: taskId={} err={}", taskId, msg);
        }
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
