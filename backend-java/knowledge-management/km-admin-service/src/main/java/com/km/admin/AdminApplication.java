package com.km.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.task.dto.CreateProcessTaskRequest;
import com.km.admin.task.dto.CreateReembedTaskRequest;
import com.km.admin.task.dto.CreateReviewReprocessTaskRequest;
import com.rabbitmq.client.Channel;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableScheduling
@EnableRabbit
@MapperScans({
    @MapperScan({
        "com.km.admin.review.mapper",
        "com.km.admin.document.mapper",
        "com.km.admin.knowledgebase.mapper"
    }),
    @MapperScan(value = "com.km.admin.config", annotationClass = Mapper.class)
})
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1/documents")
class TaskController {
    private final TaskCommandService commandService;
    private final JdbcTemplate jdbcTemplate;

    TaskController(TaskCommandService commandService, JdbcTemplate jdbcTemplate) {
        this.commandService = commandService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{docId}/tasks")
    public List<Map<String, Object>> tasks(@PathVariable Long docId) {
        return jdbcTemplate.queryForList(
                "select id, doc_id, task_type, trigger_source, task_status, progress, error_stage, error_message, retry_count, created_at, started_at, finished_at " +
                        "from km_document_process_task where doc_id=? order by id desc",
                docId);
    }

    @PostMapping("/{docId}/retry")
    public Map<String, Object> retry(@PathVariable Long docId,
                                     @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        Long taskId = commandService.createUserRetryTask(docId, userId);
        return Collections.singletonMap("taskId", taskId);
    }
}

@RestController
@RequestMapping("/internal/km/tasks")
class InternalTaskController {
    private final WorkerLeaseService workerLeaseService;
    private final DocumentTaskFacade documentTaskFacade;

    InternalTaskController(WorkerLeaseService workerLeaseService, DocumentTaskFacade documentTaskFacade) {
        this.workerLeaseService = workerLeaseService;
        this.documentTaskFacade = documentTaskFacade;
    }

    @PostMapping("/{taskId}/claim")
    public Map<String, Object> claim(@PathVariable Long taskId, @RequestBody Map<String, String> body) {
        boolean ok = workerLeaseService.claim(taskId, body.get("claimToken"));
        return Collections.singletonMap("claimed", ok);
    }

    @PostMapping("/{taskId}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable Long taskId, @RequestBody Map<String, String> body) {
        boolean ok = workerLeaseService.refresh(taskId, body.get("claimToken"));
        return Collections.singletonMap("active", ok);
    }

    @PostMapping("/process")
    public Map<String, Object> createProcess(@RequestBody CreateProcessTaskRequest req) {
        Long id = documentTaskFacade.createProcessTask(req.getDocId(), req.getUserId());
        return Collections.singletonMap("taskId", id);
    }

    @PostMapping("/reembed")
    public Map<String, Object> createReembed(@RequestBody CreateReembedTaskRequest req) {
        Long id = documentTaskFacade.createReembedTask(req.getDocId(), req.getChunkId(),
                req.getOperatorUserId(), req.getChunkContentVersion());
        return Collections.singletonMap("taskId", id);
    }

    @PostMapping("/review-reprocess")
    public Map<String, Object> createReviewReprocess(@RequestBody CreateReviewReprocessTaskRequest req) {
        Long id = documentTaskFacade.createReviewRejectedReprocessTask(req.getDocId(),
                req.getSourceReviewId(), req.getOperatorUserId());
        return Collections.singletonMap("taskId", id);
    }
}

/**
 * 文档模块对任务层的公开门面见 {@link DocumentTaskFacade}（独立成文件）。
 */

@Service
class TaskCommandService {
    private final TaskCommandTxService txService;
    private final JdbcTemplate jdbcTemplate;
    private final int userQuota;

    TaskCommandService(TaskCommandTxService txService,
                       JdbcTemplate jdbcTemplate,
                       @Value("${km.task.user-quota:2}") int userQuota) {
        this.txService = txService;
        this.jdbcTemplate = jdbcTemplate;
        this.userQuota = userQuota;
    }

    public Long createProcessTask(Long docId, String userId) {
        CreateTaskCommand cmd = new CreateTaskCommand("PROCESS", docId, userId, "USER_UPLOAD");
        cmd.idempotencyKey = "PROCESS:" + docId;
        return createWithQuota(cmd);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createReembedTask(Long docId, Long chunkId, String operatorUserId,
                                  Long requestedContentVersion) {
        if (docId == null || chunkId == null) {
            throw new IllegalArgumentException("docId and chunkId must not be null");
        }

        Map<String, Object> chunk = findActiveChunkForReembed(docId, chunkId);
        Long kbId = toLong(chunk.get("kbId"));
        Long versionNo = toLong(chunk.get("versionNo"));
        Long chunkIndex = toLong(chunk.get("chunkIndex"));
        Long contentVersion = toLong(chunk.get("contentVersion"));
        String content = toText(chunk.get("content"));
        String vectorId = toText(chunk.get("vectorId"));

        if (kbId == null || versionNo == null || versionNo < 1
                || chunkIndex == null || chunkIndex < 1
                || contentVersion == null || contentVersion < 1) {
            throw new IllegalStateException("Invalid REEMBED chunk metadata: chunkId=" + chunkId);
        }
        if (requestedContentVersion != null
                && !requestedContentVersion.equals(contentVersion)) {
            throw new IllegalStateException(
                    "Chunk content version changed: requested=" + requestedContentVersion
                            + ", actual=" + contentVersion);
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("Chunk content is empty: chunkId=" + chunkId);
        }
        if (vectorId == null || vectorId.trim().isEmpty()) {
            vectorId = "doc_" + docId + "_v_" + versionNo + "_idx_" + chunkIndex;
        }

        Map<String, Object> chunkPayload = new java.util.LinkedHashMap<String, Object>();
        chunkPayload.put("chunkId", chunkId);
        chunkPayload.put("docId", docId);
        chunkPayload.put("kbId", kbId);
        chunkPayload.put("versionNo", versionNo);
        chunkPayload.put("chunkIndex", chunkIndex);
        chunkPayload.put("content", content);
        chunkPayload.put("chapterPath", chunk.get("chapterPath"));
        chunkPayload.put("pageNo", chunk.get("pageNo"));

        String chunkType = toText(chunk.get("chunkType"));
        chunkPayload.put("chunkType",
                chunkType == null || chunkType.trim().isEmpty() ? "paragraph" : chunkType);

        Long charCount = toLong(chunk.get("charCount"));
        chunkPayload.put("charCount", charCount == null ? content.length() : charCount);
        chunkPayload.put("vectorId", vectorId);
        chunkPayload.put("contentVersion", contentVersion);

        CreateTaskCommand cmd = new CreateTaskCommand(
                "REEMBED", docId, operatorUserId, "REVIEW_EDIT");
        cmd.payload.put("chunkId", chunkId);
        cmd.payload.put("chunkContentVersion", contentVersion);
        cmd.payload.put("chunks", Collections.singletonList(chunkPayload));
        cmd.idempotencyKey = "REEMBED:" + chunkId + ":" + contentVersion;
        return txService.createTask(cmd);
    }

    public Long createReviewRejectedReprocessTask(Long docId, Long sourceReviewId, String operatorUserId) {
        CreateTaskCommand cmd = new CreateTaskCommand("REPROCESS", docId, operatorUserId, "REVIEW_REJECTED");
        cmd.payload.put("sourceReviewId", sourceReviewId);
        cmd.idempotencyKey = "REVIEW_REPROCESS:" + docId + ":" + sourceReviewId;
        return txService.createTask(cmd);
    }

    public Long createStrategyReprocessTask(Long docId, Long strategyVersion, String operatorUserId) {
        CreateTaskCommand cmd = new CreateTaskCommand("REPROCESS", docId, operatorUserId, "STRATEGY_CHANGE");
        cmd.strategyVersion = strategyVersion;
        cmd.payload.put("strategyVersion", strategyVersion);
        cmd.idempotencyKey = "REPROCESS:" + docId + ":" + strategyVersion;
        return txService.createTask(cmd);
    }

    public Long createKnowledgeBaseReprocessTask(Long docId, Long strategyVersion, String operatorUserId,
                                                 String triggerSource, String idempotencyKey,
                                                 Map<String, Object> payload) {
        CreateTaskCommand cmd = new CreateTaskCommand("REPROCESS", docId, operatorUserId, triggerSource);
        cmd.strategyVersion = strategyVersion;
        if (payload != null) {
            cmd.payload.putAll(payload);
        }
        cmd.payload.put("operation", "REPROCESS");
        cmd.payload.put("docId", docId);
        cmd.payload.put("strategyVersion", strategyVersion);
        cmd.idempotencyKey = idempotencyKey;
        return txService.createTask(cmd);
    }

    /**
     * 系统任务，userId 固定为 null（R14：系统 PURGE 任务的 UUID 字段保持 null）。
     */
    public Long createPurgeTask(Long docId, LocalDateTime deletedAt) {
        CreateTaskCommand cmd = new CreateTaskCommand("PURGE", docId, null, "RETENTION_EXPIRED");
        cmd.idempotencyKey = "PURGE:" + docId + ":" + deletedAt;
        return txService.createTask(cmd);
    }

    public Long createUserRetryTask(Long docId, String userId) {
        TaskRow failed = jdbcTemplate.query(
                "select * from km_document_process_task where doc_id=? and task_status='FAILED' " +
                        "and task_type in ('PROCESS','REPROCESS','REEMBED') order by id desc limit 1",
                taskMapper(), docId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("没有可重试的失败任务"));
        if (failed.retryCount >= 3) {
            throw new IllegalStateException("重试次数已达到上限 3 次");
        }
        CreateTaskCommand cmd = new CreateTaskCommand(failed.taskType, docId, userId, "USER_RETRY");
        cmd.sourceTaskId = failed.id;
        cmd.retryCount = failed.retryCount + 1;
        cmd.idempotencyKey = "RETRY:" + failed.id + ":" + cmd.retryCount;
        return createWithQuota(cmd);
    }

    private Map<String, Object> findActiveChunkForReembed(Long docId, Long chunkId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select c.id as chunkId, c.doc_id as docId, d.kb_id as kbId, " +
                        "c.version_no as versionNo, c.chunk_index as chunkIndex, " +
                        "c.content as content, c.content_version as contentVersion, " +
                        "c.chapter_path as chapterPath, c.page_no as pageNo, " +
                        "c.chunk_type as chunkType, c.char_count as charCount, " +
                        "c.vector_id as vectorId " +
                        "from km_document_chunk c " +
                        "join km_document d on d.id=c.doc_id " +
                        "where c.id=? and c.doc_id=? and c.is_active=1 " +
                        "and d.is_deleted=0 for update",
                chunkId, docId);
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Active chunk not found: docId=" + docId + ", chunkId=" + chunkId);
        }
        return rows.get(0);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number
                ? ((Number) value).longValue()
                : Long.valueOf(String.valueOf(value));
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long createWithQuota(CreateTaskCommand cmd) {
        // 系统任务（userId == null）跳过用户配额检查
        if (cmd.userId == null) {
            return txService.createTask(cmd);
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from km_document_process_task where user_id=? and trigger_source in ('USER_UPLOAD','USER_RETRY') " +
                        "and task_type in ('PROCESS','REPROCESS') and task_status in ('QUEUED','RUNNING')",
                Integer.class, cmd.userId);
        if (count != null && count >= userQuota) {
            throw new IllegalStateException("当前用户处理中任务已达到上限 " + userQuota);
        }
        return txService.createTask(cmd);
    }

    private RowMapper<TaskRow> taskMapper() {
        return (rs, rowNum) -> {
            TaskRow t = new TaskRow();
            t.id = rs.getLong("id");
            t.taskType = rs.getString("task_type");
            t.retryCount = rs.getInt("retry_count");
            return t;
        };
    }
}

@Service
class TaskCommandTxService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TaskDispatchPublisher publisher;

    TaskCommandTxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, TaskDispatchPublisher publisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createTask(CreateTaskCommand cmd) {
        Map<String, Object> doc = findDocumentForUpdate(cmd.docId);
        TaskRow existing = findByIdempotencyKey(cmd.idempotencyKey);
        if (existing != null) {
            return existing.id;
        }

        Long targetVersionNo = null;
        if ("REPROCESS".equals(cmd.taskType)) {
            targetVersionNo = allocateBuildingVersion(cmd.docId, cmd.strategyVersion);
            cmd.payload.put("targetVersionNo", targetVersionNo);
        }

        String payloadJson = toJson(cmd.payload);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        final Long finalTargetVersionNo = targetVersionNo;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into km_document_process_task(doc_id,kb_id,user_id,task_type,trigger_source,task_status,progress,trace_id,task_payload_json," +
                            "idempotency_key,source_task_id,strategy_version,target_version_no,retry_count,dispatch_status,created_at,updated_at) " +
                            "values(?,?,?,?,?,'QUEUED',0,?,?,?,?,?,?,?,'PENDING',now(),now())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, cmd.docId);
            ps.setObject(2, doc.get("kb_id"));
            // 必改 2.2：userId 是 String UUID；系统任务为 null
            if (cmd.userId == null) {
                ps.setNull(3, java.sql.Types.VARCHAR);
            } else {
                ps.setString(3, cmd.userId);
            }
            ps.setString(4, cmd.taskType);
            ps.setString(5, cmd.triggerSource);
            ps.setString(6, traceId);
            ps.setString(7, payloadJson);
            ps.setString(8, cmd.idempotencyKey);
            ps.setObject(9, cmd.sourceTaskId);
            ps.setObject(10, cmd.strategyVersion);
            ps.setObject(11, finalTargetVersionNo);
            ps.setInt(12, cmd.retryCount);
            return ps;
        }, keyHolder);
        Long taskId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        if ("REPROCESS".equals(cmd.taskType) && finalTargetVersionNo != null) {
            jdbcTemplate.update("update km_document_version set task_id=? where doc_id=? and version_no=?",
                    taskId, cmd.docId, finalTargetVersionNo);
        }
        KmTaskMessage msg = KmTaskMessage.from(taskId, cmd, doc, traceId, finalTargetVersionNo, payloadJson);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.sendNowAndConfirm("km.exchange", routingKey(cmd.taskType), msg);
            }
        });
        return taskId;
    }

    private Map<String, Object> findDocumentForUpdate(Long docId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from km_document where id=? for update", docId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("文档不存在：" + docId);
        }
        return rows.get(0);
    }

    private Long allocateBuildingVersion(Long docId, Long strategyVersion) {
        Map<String, Object> doc = findDocumentForUpdate(docId);
        Number next = (Number) doc.getOrDefault("next_version_no", 1L);
        Long versionNo = next.longValue();
        jdbcTemplate.update("update km_document set next_version_no=? where id=?", versionNo + 1, docId);
        jdbcTemplate.update("insert into km_document_version(doc_id,version_no,strategy_version,version_status,created_at) values(?,?,?,'BUILDING',now())",
                docId, versionNo, strategyVersion);
        return versionNo;
    }

    private TaskRow findByIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }
        List<TaskRow> rows = jdbcTemplate.query(
                "select id, task_type, retry_count from km_document_process_task where idempotency_key=?",
                (rs, i) -> {
                    TaskRow t = new TaskRow();
                    t.id = rs.getLong("id");
                    t.taskType = rs.getString("task_type");
                    t.retryCount = rs.getInt("retry_count");
                    return t;
                }, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String routingKey(String type) {
        if ("PROCESS".equals(type)) return "km.doc.process";
        if ("REPROCESS".equals(type)) return "km.doc.reprocess";
        if ("REEMBED".equals(type)) return "km.chunk.reembed";
        return "km.doc.purge";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

@Service
class TaskDispatchPublisher {
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    TaskDispatchPublisher(JdbcTemplate jdbcTemplate, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendNowAndConfirm(String exchange, String routingKey, KmTaskMessage msg) {
        int claimed = jdbcTemplate.update(
                "update km_document_process_task set dispatch_status='DISPATCHING', dispatch_attempts=dispatch_attempts+1, " +
                        "last_dispatch_attempt_at=now(), updated_at=now() where id=? and task_status='QUEUED' and dispatch_status='PENDING'",
                msg.taskId);
        if (claimed == 0) {
            return;
        }
        try {
            CorrelationData cd = new CorrelationData("task-" + msg.taskId + "-" + System.currentTimeMillis());
            rabbitTemplate.send(exchange, routingKey, MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(msg))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .build(), cd);
            CorrelationData.Confirm confirm = cd.getFuture().get(10, TimeUnit.SECONDS);
            if (confirm != null && confirm.isAck()) {
                jdbcTemplate.update("update km_document_process_task set dispatch_status='PUBLISHED', published_at=now(), updated_at=now() where id=? and task_status='QUEUED'",
                        msg.taskId);
            } else {
                throw new IllegalStateException(confirm == null ? "RabbitMQ confirm timeout" : confirm.getReason());
            }
        } catch (Exception e) {
            jdbcTemplate.update("update km_document_process_task set dispatch_status='PENDING', error_stage='DISPATCH', error_message=?, updated_at=now() " +
                    "where id=? and task_status='QUEUED' and dispatch_status='DISPATCHING'", shortMessage(e), msg.taskId);
        }
    }

    private String shortMessage(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 900 ? msg.substring(0, 900) : msg;
    }
}

@Service
class WorkerLeaseService {
    private final JdbcTemplate jdbcTemplate;

    WorkerLeaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claim(Long taskId, String claimToken) {
        int updated = jdbcTemplate.update(
                "update km_document_process_task set task_status='RUNNING', worker_claim_token=?, worker_claim_expire_at=date_add(now(), interval 5 minute), " +
                        "started_at=coalesce(started_at, now()), updated_at=now() where id=? and " +
                        "((task_status='QUEUED' and dispatch_status in ('DISPATCHING','PUBLISHED')) or (task_status='RUNNING' and worker_claim_expire_at < now()))",
                claimToken, taskId);
        return updated == 1;
    }

    public boolean refresh(Long taskId, String claimToken) {
        int updated = jdbcTemplate.update(
                "update km_document_process_task set worker_claim_expire_at=date_add(now(), interval 5 minute), updated_at=now() " +
                        "where id=? and task_status='RUNNING' and worker_claim_token=?",
                taskId, claimToken);
        return updated == 1;
    }
}

@Component
class TaskRepublishScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final TaskDispatchPublisher publisher;
    private final ObjectMapper objectMapper;

    TaskRepublishScheduler(JdbcTemplate jdbcTemplate, TaskDispatchPublisher publisher, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStuckDispatch() {
        jdbcTemplate.update("update km_document_process_task set dispatch_status='PENDING', updated_at=now() " +
                "where task_status='QUEUED' and dispatch_status='DISPATCHING' and last_dispatch_attempt_at < date_sub(now(), interval 5 minute)");
        jdbcTemplate.update("update km_document_process_task set dispatch_status='PENDING', updated_at=now() " +
                "where task_status='QUEUED' and dispatch_status='PUBLISHED' and published_at < date_sub(now(), interval 5 minute) and worker_claim_token is null");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from km_document_process_task where task_status='QUEUED' and dispatch_status='PENDING' " +
                        "and coalesce(last_dispatch_attempt_at, created_at) < date_sub(now(), interval 5 minute) limit 50");
        for (Map<String, Object> row : rows) {
            KmTaskMessage msg = KmTaskMessage.fromRow(row, objectMapper);
            publisher.sendNowAndConfirm("km.exchange", routingKey(String.valueOf(row.get("task_type"))), msg);
        }
    }

    private String routingKey(String type) {
        if ("PROCESS".equals(type)) return "km.doc.process";
        if ("REPROCESS".equals(type)) return "km.doc.reprocess";
        if ("REEMBED".equals(type)) return "km.chunk.reembed";
        return "km.doc.purge";
    }
}

@Component
class TaskResultConsumer {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    TaskResultConsumer(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "km.task.result")
    @Transactional(rollbackFor = Exception.class)
    public void onResult(byte[] body, Channel channel, @org.springframework.messaging.handler.annotation.Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        KmTaskResultMessage msg = objectMapper.readValue(new String(body, StandardCharsets.UTF_8), KmTaskResultMessage.class);
        Map<String, Object> task = jdbcTemplate.queryForList("select * from km_document_process_task where id=? for update", msg.taskId)
                .stream().findFirst().orElse(null);
        if (task == null) {
            channel.basicAck(tag, false);
            return;
        }
        if (msg.claimToken == null || !msg.claimToken.equals(task.get("worker_claim_token"))) {
            channel.basicAck(tag, false);
            return;
        }
        int inserted = jdbcTemplate.update("insert ignore into km_processed_event(event_id, task_id, doc_id, event_type, created_at) values(?,?,?,?,now())",
                msg.eventId, msg.taskId, msg.docId, msg.eventType);
        if (inserted == 0) {
            channel.basicAck(tag, false);
            return;
        }
        Number lastSeq = (Number) task.getOrDefault("last_event_seq", 0);
        if (msg.eventSeq <= lastSeq.intValue()) {
            channel.basicAck(tag, false);
            return;
        }
        if (msg.success == null) {
            jdbcTemplate.update("update km_document_process_task set progress=?, error_stage=null, last_event_seq=?, last_event_id=?, updated_at=now() where id=?",
                    msg.progress, msg.eventSeq, msg.eventId, msg.taskId);
            writeLog(msg.docId, msg.stage, "RUNNING", null);
        } else if (msg.success) {
            success(msg);
        } else {
            failure(msg);
        }
        channel.basicAck(tag, false);
    }

    private void success(KmTaskResultMessage msg) {
        if ("PROCESS".equals(msg.taskType)) {
            insertChunks(msg, true);
            jdbcTemplate.update("update km_document set document_status='PENDING_REVIEW', updated_at=now() where id=?", msg.docId);
        } else if ("REPROCESS".equals(msg.taskType)) {
            insertChunks(msg, false);
            jdbcTemplate.update("update km_document_version set version_status='PENDING_REVIEW' where doc_id=? and version_no=?",
                    msg.docId, msg.targetVersionNo);
            jdbcTemplate.update("update km_document set document_status='READY', updated_at=now() where id=?",
                    msg.docId);
        } else if ("REEMBED".equals(msg.taskType)) {
            Map<String, Object> payload = msg.taskPayloadJson == null ? Collections.emptyMap() : readMap(msg.taskPayloadJson);
            Object chunkId = payload.get("chunkId");
            jdbcTemplate.update("update km_document_chunk set vector_status='READY', vector_id=?, updated_at=now() where id=?",
                    msg.vectorIds == null || msg.vectorIds.isEmpty() ? null : msg.vectorIds.get(0), chunkId);
        } else if ("PURGE".equals(msg.taskType)) {
            purgeSuccess(msg);
            return;
        }
        jdbcTemplate.update("update km_document_process_task set task_status='SUCCESS', progress=100, finished_at=now(), last_event_seq=?, last_event_id=?, updated_at=now() where id=?",
                msg.eventSeq, msg.eventId, msg.taskId);
        writeLog(msg.docId, msg.stage, "SUCCESS", null);
    }

    private void insertChunks(KmTaskResultMessage msg, boolean active) {
        if (msg.chunks == null || msg.chunks.isEmpty()) {
            return;
        }
        for (Map<String, Object> chunk : msg.chunks) {
            long versionNo = number(chunk.get("versionNo"), msg.targetVersionNo == null ? 1L : msg.targetVersionNo);
            int chunkIndex = (int) number(chunk.get("chunkIndex"), 0L);
            String content = text(chunk.get("content"));
            String chapterPath = text(chunk.get("chapterPath"));
            Object pageNo = chunk.get("pageNo");
            String chunkType = text(chunk.get("chunkType"));
            int charCount = (int) number(chunk.get("charCount"), (long) content.length());
            String vectorId = text(chunk.get("vectorId"));
            jdbcTemplate.update("insert into km_document_chunk(doc_id, version_no, chunk_index, content, chapter_path, page_no, chunk_type, char_count, vector_id, vector_status, is_active, created_at, updated_at) " +
                            "values(?,?,?,?,?,?,?,?,?,'READY',?,now(),now())",
                    msg.docId, versionNo, chunkIndex, content, chapterPath, pageNo, chunkType, charCount, vectorId, active ? 1 : 0);
        }
    }

    private void failure(KmTaskResultMessage msg) {
        jdbcTemplate.update("update km_document_process_task set task_status='FAILED', error_stage=?, error_message=?, finished_at=now(), last_event_seq=?, last_event_id=?, updated_at=now() where id=?",
                msg.errorStage, msg.errorMessage, msg.eventSeq, msg.eventId, msg.taskId);
        if ("PROCESS".equals(msg.taskType) || "REPROCESS".equals(msg.taskType)) {
            if ("REPROCESS".equals(msg.taskType)) {
                jdbcTemplate.update("update km_document_version set version_status='FAILED' where doc_id=? and version_no=?",
                        msg.docId, msg.targetVersionNo);
            } else {
                jdbcTemplate.update("update km_document set document_status='FAILED', error_stage=?, error_message=?, updated_at=now() where id=?",
                        msg.errorStage, msg.errorMessage, msg.docId);
            }
        }
        if ("PURGE".equals(msg.taskType)) {
            jdbcTemplate.update("insert into km_purge_audit(doc_id, task_id, purge_status, error_stage, error_message, created_at) values(?,?,'FAILED',?,?,now())",
                    msg.docId, msg.taskId, msg.errorStage, msg.errorMessage);
        }
        writeLog(msg.docId, msg.stage, "FAILED", msg.errorMessage);
    }

    private void switchVersion(KmTaskResultMessage msg) {
        jdbcTemplate.update("update km_document_version set version_status='RETIRED' where doc_id=? and version_status='ACTIVE'", msg.docId);
        jdbcTemplate.update("update km_document_version set version_status='ACTIVE', activated_at=now() where doc_id=? and version_no=?",
                msg.docId, msg.targetVersionNo);
        jdbcTemplate.update("update km_document_chunk set is_active=0 where doc_id=?", msg.docId);
        jdbcTemplate.update("update km_document_chunk set is_active=1 where doc_id=? and version_no=?", msg.docId, msg.targetVersionNo);
        jdbcTemplate.update("insert into km_vector_cleanup_task(doc_id, keep_version_no, cleanup_status, created_at) values(?,?,'PENDING',now())",
                msg.docId, msg.targetVersionNo);
    }

    private void purgeSuccess(KmTaskResultMessage msg) {
        jdbcTemplate.update("insert into km_purge_audit(doc_id, task_id, object_key, purge_status, purged_at, created_at) values(?,?,?,'SUCCESS',now(),now())",
                msg.docId, msg.taskId, msg.objectKey);
        jdbcTemplate.update("delete from km_document_chunk where doc_id=?", msg.docId);
        jdbcTemplate.update("delete from km_document_status_log where doc_id=?", msg.docId);
        jdbcTemplate.update("delete from km_processed_event where doc_id=? or task_id=?", msg.docId, msg.taskId);
        jdbcTemplate.update("delete from km_document_process_task where doc_id=?", msg.docId);
        jdbcTemplate.update("delete from km_document where id=?", msg.docId);
    }

    private void writeLog(Long docId, String stage, String status, String message) {
        jdbcTemplate.update("insert into km_document_status_log(doc_id, stage, status, message, created_at) values(?,?,?,?,now())",
                docId, stage, status, message);
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private long number(Object value, Long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback == null ? 0L : fallback;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

/**
 * 回收站 30 天清理任务调度器。R7：复用现有 Scheduler。
 * - 每 30 分钟扫描一次
 * - 命中 deleted_at <= now - 30 days 且无未完成 PURGE 任务的文档
 * - 调用 DocumentTaskFacade.createPurgeTask
 */
@Component
class PurgeScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final DocumentTaskFacade documentTaskFacade;

    PurgeScheduler(JdbcTemplate jdbcTemplate, DocumentTaskFacade documentTaskFacade) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentTaskFacade = documentTaskFacade;
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    public void createPurgeTasks() {
        List<Map<String, Object>> docs = jdbcTemplate.queryForList(
                "select id, deleted_at from km_document d where d.is_deleted=1 and d.deleted_at <= date_sub(now(), interval 30 day) " +
                        "and not exists(select 1 from km_document_process_task t where t.doc_id=d.id and t.task_type='PURGE' and t.task_status in ('QUEUED','RUNNING')) " +
                        "order by d.id limit 100");
        for (Map<String, Object> doc : docs) {
            Object deletedAt = doc.get("deleted_at");
            LocalDateTime when = deletedAt instanceof LocalDateTime
                    ? (LocalDateTime) deletedAt
                    : LocalDateTime.now().minusDays(31);
            documentTaskFacade.createPurgeTask(((Number) doc.get("id")).longValue(), when);
        }
    }
}

@Service
class KnowledgeBaseReprocessService {
    private final JdbcTemplate jdbcTemplate;
    private final TaskCommandService taskCommandService;

    KnowledgeBaseReprocessService(JdbcTemplate jdbcTemplate, TaskCommandService taskCommandService) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskCommandService = taskCommandService;
    }

    /**
     * 必改 2.5：operatorId 改为 String UUID。
     */
    public void createReprocessTasks(Long kbId, Long strategyVersion, String operatorId) {
        long lastId = 0L;
        while (true) {
            List<Long> docIds = jdbcTemplate.queryForList(
                    "select id from km_document where kb_id=? and id>? and is_deleted=0 and document_status='READY' order by id limit 100",
                    Long.class, kbId, lastId);
            if (docIds.isEmpty()) {
                break;
            }
            for (Long docId : docIds) {
                try {
                    taskCommandService.createStrategyReprocessTask(docId, strategyVersion, operatorId);
                } catch (Exception ignored) {
                    // 单文档失败不回滚整批任务，后续由日志或补偿脚本处理。
                }
                lastId = docId;
            }
        }
    }
}

/**
 * 必改 2.1：userId 改为 String UUID。
 */
class CreateTaskCommand {
    String taskType;
    Long docId;
    String userId;
    String triggerSource;
    String idempotencyKey;
    Long sourceTaskId;
    Long strategyVersion;
    Integer retryCount = 0;
    Map<String, Object> payload = new LinkedHashMap<>();

    CreateTaskCommand(String taskType, Long docId, String userId, String triggerSource) {
        this.taskType = taskType;
        this.docId = docId;
        this.userId = userId;
        this.triggerSource = triggerSource;
    }
}

class TaskRow {
    Long id;
    String taskType;
    int retryCount;
}

class KmTaskMessage {
    public Long taskId;
    public Long docId;
    public Long kbId;
    public String taskType;
    public String triggerSource;
    public String traceId;
    public String filePath;
    public String extension;
    public Long chunkId;
    public Long strategyVersion;
    public Long targetVersionNo;
    public String taskPayloadJson;

    static KmTaskMessage from(Long taskId, CreateTaskCommand cmd, Map<String, Object> doc, String traceId, Long targetVersionNo, String payloadJson) {
        KmTaskMessage m = new KmTaskMessage();
        m.taskId = taskId;
        m.docId = cmd.docId;
        Object kbId = doc.get("kb_id");
        m.kbId = kbId == null ? null : ((Number) kbId).longValue();
        m.taskType = cmd.taskType;
        m.triggerSource = cmd.triggerSource;
        m.traceId = traceId;
        m.filePath = String.valueOf(doc.getOrDefault("object_key", ""));
        m.extension = String.valueOf(doc.getOrDefault("extension", ""));
        Object chunkId = cmd.payload.get("chunkId");
        m.chunkId = chunkId == null ? null : Long.valueOf(String.valueOf(chunkId));
        m.strategyVersion = cmd.strategyVersion;
        m.targetVersionNo = targetVersionNo;
        m.taskPayloadJson = payloadJson;
        return m;
    }

    static KmTaskMessage fromRow(Map<String, Object> row, ObjectMapper mapper) {
        KmTaskMessage m = new KmTaskMessage();
        m.taskId = ((Number) row.get("id")).longValue();
        m.docId = ((Number) row.get("doc_id")).longValue();
        Object kbId = row.get("kb_id");
        m.kbId = kbId == null ? null : ((Number) kbId).longValue();
        m.taskType = String.valueOf(row.get("task_type"));
        m.triggerSource = String.valueOf(row.get("trigger_source"));
        m.traceId = String.valueOf(row.get("trace_id"));
        m.strategyVersion = row.get("strategy_version") == null ? null : ((Number) row.get("strategy_version")).longValue();
        m.targetVersionNo = row.get("target_version_no") == null ? null : ((Number) row.get("target_version_no")).longValue();
        m.taskPayloadJson = row.get("task_payload_json") == null ? "{}" : String.valueOf(row.get("task_payload_json"));
        return m;
    }
}

class KmTaskResultMessage {
    public String eventId;
    public int eventSeq;
    public String eventType;
    public Long taskId;
    public Long docId;
    public String taskType;
    public String traceId;
    public String claimToken;
    public Boolean success;
    public Integer progress;
    public String stage;
    public String errorStage;
    public String errorMessage;
    public List<Map<String, Object>> chunks;
    public List<String> vectorIds;
    public String taskPayloadJson;
    public Long targetVersionNo;
    public String objectKey;
}
