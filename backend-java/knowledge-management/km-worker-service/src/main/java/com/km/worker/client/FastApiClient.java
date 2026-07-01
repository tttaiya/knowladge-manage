package com.km.worker.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.worker.DynamicConfigHolder;
import com.km.worker.exception.AiStageException;
import com.km.worker.messaging.KmTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F4 整合（commit #24）：Worker FastAPI 客户端（抽出独立文件，硬规则要求）。
 *
 * 关键变更（v1.0 文档 6.3 / 6.4）：
 * - parse 请求体直接构造 WorkerTaskRequest（不再用 {task, parsed} 包装）
 * - chunk 请求体由 task + parseResponse 重新组装，**禁止**整体嵌套 parseResponse
 * - 每次调用都 assertSuccess；success=false 必须抛 AiStageException
 * - 统一携带 X-Internal-Token
 * - 连接超时 / 读取超时通过 application.yml 配置（km.ai.connect-timeout-ms / km.ai.read-timeout-ms）
 */
@Service
public class FastApiClient {

    private static final Logger log = LoggerFactory.getLogger(FastApiClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String aiBaseUrl;
    private final String internalToken;
    private final DynamicConfigHolder configHolder;

    public FastApiClient(
            RestTemplateBuilder builder,
            ObjectMapper objectMapper,
            @Value("${km.ai-base-url}") String aiBaseUrl,
            @Value("${km.ai.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${km.ai.read-timeout-ms:30000}") int readTimeoutMs,
            @Value("${km.internal.token:demo-internal-token}") String internalToken,
            DynamicConfigHolder configHolder) {
        this.objectMapper = objectMapper;
        this.aiBaseUrl = aiBaseUrl;
        this.internalToken = internalToken;
        this.configHolder = configHolder;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    /**
     * F4 正式接口 1：parse
     * 请求体直接构造 WorkerTaskRequest 风格（taskId/docId/kbId/traceId/filePath/extension/taskPayloadJson）。
     */
    public Map<String, Object> parse(KmTaskMessage msg, String stagedFilePath) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", msg.taskId);
        body.put("docId", msg.docId);
        body.put("kbId", msg.kbId);
        body.put("taskType", msg.taskType);
        body.put("traceId", msg.traceId);
        body.put("filePath", stagedFilePath);          // F4 接收容器内路径
        body.put("extension", msg.extension);          // F4 校验
        body.put("targetVersionNo", msg.targetVersionNo == null ? 1L : msg.targetVersionNo);
        body.put("taskPayloadJson", configHolder.applyParserDefaults(msg.taskPayloadJson));
        return assertSuccess(post("/internal/ai/parse", body), "PARSE", msg.traceId);
    }

    /**
     * F4 正式接口 2：chunk
     * 请求体由 task + parseResponse 重新组装，**禁止**整体嵌套 parseResponse。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chunk(KmTaskMessage msg, Map<String, Object> parseResponse) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", msg.taskId);
        body.put("docId", msg.docId);
        body.put("kbId", msg.kbId);
        body.put("traceId", msg.traceId);
        body.put("blocks", parseResponse.get("blocks"));           // parseResponse.blocks 原样传入
        body.put("parsedText", parseResponse.get("parsedText"));   // 兼容
        body.put("taskPayloadJson", configHolder.applyParserDefaults(msg.taskPayloadJson));
        return assertSuccess(post("/internal/ai/chunk", body), "CHUNK", msg.traceId);
    }

    /**
     * F5 mock（commit #24 保持原协议；F5 真实实现由黄依诺合并）
     */
    public Map<String, Object> embed(KmTaskMessage msg, Object chunks) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operation", "EMBED");
        body.put("task", msg);
        body.put("chunks", chunks);
        return assertSuccess(post("/internal/ai/embed", body), "EMBED", msg.traceId);
    }

    /**
     * F5 mock（commit #24 保持原协议）
     */
    public Map<String, Object> reembed(KmTaskMessage msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operation", "REEMBED");
        body.put("task", msg);
        body.put("chunks", readReembedChunks(msg));
        return assertSuccess(post("/internal/ai/embed", body), "EMBED", msg.traceId);
    }

    /**
     * 删除向量（document 或 version 级）；失败返回 false，PURGE 链路将其标记为 CHROMA 阶段失败
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readReembedChunks(KmTaskMessage msg) {
        if (msg == null || msg.taskPayloadJson == null
                || msg.taskPayloadJson.trim().isEmpty()) {
            throw new AiStageException(
                    "EMBED", "REEMBED taskPayloadJson is empty",
                    msg == null ? null : msg.traceId);
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    msg.taskPayloadJson,
                    new TypeReference<Map<String, Object>>() { });
            Object chunksValue = payload.get("chunks");
            if (!(chunksValue instanceof List) || ((List<?>) chunksValue).isEmpty()) {
                throw new AiStageException(
                        "EMBED",
                        "REEMBED taskPayloadJson.chunks must be a non-empty array",
                        msg.traceId);
            }
            for (Object chunk : (List<?>) chunksValue) {
                if (!(chunk instanceof Map)) {
                    throw new AiStageException(
                            "EMBED", "REEMBED chunks item must be an object",
                            msg.traceId);
                }
            }
            return (List<Map<String, Object>>) chunksValue;
        } catch (AiStageException e) {
            throw e;
        } catch (Exception e) {
            throw new AiStageException(
                    "EMBED",
                    "Failed to parse REEMBED taskPayloadJson: " + e.getMessage(),
                    msg.traceId);
        }
    }

    public boolean deleteVectors(Long docId) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.set(INTERNAL_TOKEN_HEADER, internalToken);
            ResponseEntity<Map> r = restTemplate.exchange(
                    aiBaseUrl + "/internal/ai/vectors/" + docId,
                    HttpMethod.DELETE, new HttpEntity<>(h), Map.class);
            Map body = r.getBody();
            if (body == null) return false;
            return Boolean.TRUE.equals(body.get("success"))
                    || Boolean.TRUE.equals(body.get("deleted"));
        } catch (Exception e) {
            log.error("deleteVectors failed for docId={}", docId, e);
            return false;
        }
    }

    /**
     * F4 commit #24：F4 业务失败 HTTP 200 + success=false 也必须当失败处理（R-F4-9）
     */
    private Map<String, Object> assertSuccess(Map<String, Object> response, String defaultStage, String traceId) {
        if (response == null) {
            throw new AiStageException(defaultStage, "AI service returned null response", traceId);
        }
        Object ok = response.get("success");
        if (!Boolean.TRUE.equals(ok)) {
            String stage = String.valueOf(response.getOrDefault("errorStage", defaultStage));
            String msg = String.valueOf(response.getOrDefault("errorMessage", "AI service failed"));
            throw new AiStageException(stage, msg, traceId);
        }
        return response;
    }

    private Map<String, Object> post(String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(INTERNAL_TOKEN_HEADER, internalToken);
        ResponseEntity<Map> r = restTemplate.postForEntity(
                aiBaseUrl + path, new HttpEntity<>(body, h), Map.class);
        return r.getBody();
    }
}
