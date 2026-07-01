package com.km.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.admin.config.dto.ConnectionTestRequest;
import com.km.admin.config.dto.ConnectionTestResult;
import com.km.admin.config.dto.EmbeddingConfigDTO;
import com.km.admin.config.dto.ParserConfigDTO;
import com.km.admin.config.dto.RerankConfigDTO;
import com.km.admin.config.entity.SystemConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfigServiceImpl implements ConfigService {

    private static final String API_KEY_MASK = "********";

    @Autowired
    private ConfigMapper configMapper;

    @Autowired
    private ConfigChangedProducer configChangedProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public EmbeddingConfigDTO getEmbeddingConfig() {
        EmbeddingConfigDTO out = configMapper.loadEmbeddingConfig();
        if (out != null) {
            out.setApiKey(API_KEY_MASK);
        }
        return out;
    }

    @Override
    public EmbeddingConfigDTO updateEmbeddingConfig(EmbeddingConfigDTO dto) {
        validateEmbeddingConfig(dto);
        updateValue("embedding.model", dto.getModel());
        updateValue("embedding.api_base", dto.getApiBase() == null ? "" : dto.getApiBase());
        updateValue("embedding.api_key", resolveApiKey("embedding.api_key", dto.getApiKey()));
        updateValue("embedding.dimension", String.valueOf(dto.getDimension()));

        long version = nextConfigVersion();
        writeAudit("embedding", version, "updated: embedding.model, embedding.api_base, embedding.dimension, embedding.api_key(masked)");
        registerAfterCommit("embedding", collectSafeConfigValues(), version);

        EmbeddingConfigDTO out = configMapper.loadEmbeddingConfig();
        out.setApiKey(API_KEY_MASK);
        return out;
    }

    @Override
    public RerankConfigDTO getRerankConfig() {
        RerankConfigDTO out = configMapper.loadRerankConfig();
        if (out != null) {
            out.setApiKey(API_KEY_MASK);
        }
        return out;
    }

    @Override
    public RerankConfigDTO updateRerankConfig(RerankConfigDTO dto) {
        validateRerankConfig(dto);
        updateValue("rerank.model", dto.getModel());
        updateValue("rerank.api_base", dto.getApiBase() == null ? "" : dto.getApiBase());
        updateValue("rerank.api_key", resolveApiKey("rerank.api_key", dto.getApiKey()));
        updateValue("rerank.top_n", String.valueOf(dto.getTopN()));
        updateValue("rerank.threshold", String.valueOf(dto.getThreshold()));

        long version = nextConfigVersion();
        writeAudit("rerank", version, "updated: rerank.model, rerank.api_base, rerank.top_n, rerank.threshold, rerank.api_key(masked)");
        registerAfterCommit("rerank", collectSafeConfigValues(), version);

        RerankConfigDTO out = configMapper.loadRerankConfig();
        out.setApiKey(API_KEY_MASK);
        return out;
    }

    @Override
    public ParserConfigDTO getParserConfig() {
        return configMapper.loadParserConfig();
    }

    @Override
    public ParserConfigDTO updateParserConfig(ParserConfigDTO dto) {
        validateParserConfig(dto);
        updateValue("parser.api_base", dto.getApiBase() == null ? "" : dto.getApiBase());
        updateValue("parser.paddleocr_enabled", String.valueOf(dto.isPaddleocrEnabled()));
        updateValue("parser.chunk_size", String.valueOf(dto.getChunkSize()));
        updateValue("parser.chunk_overlap", String.valueOf(dto.getChunkOverlap()));
        updateValue("parser.max_concurrent_tasks", String.valueOf(dto.getMaxConcurrentTasks()));
        updateValue("parser.max_retry_count", String.valueOf(dto.getMaxRetryCount()));
        updateValue("parser.timeout_seconds", String.valueOf(dto.getTimeoutSeconds()));

        long version = nextConfigVersion();
        writeAudit("parser", version, "updated: parser.api_base, parser.paddleocr_enabled, parser.chunk_size, parser.chunk_overlap, parser.max_concurrent_tasks, parser.max_retry_count, parser.timeout_seconds");
        registerAfterCommit("parser", collectSafeConfigValues(), version);

        return dto;
    }

    @Override
    public ConnectionTestResult testConnection(ConnectionTestRequest req) {
        fillSavedConnectionFields(req);
        ConnectionTestResult invalid = validateConnectionRequest(req);
        if (invalid != null) {
            return invalid;
        }

        String type = req.getType().trim().toLowerCase();
        String apiBase = trimTrailingSlash(req.getApiBase().trim());
        long start = System.currentTimeMillis();
        try {
            ConnectionTestResult result;
            if ("embedding".equals(type)) {
                result = testEmbedding(apiBase, req);
            } else if ("rerank".equals(type)) {
                result = testRerank(apiBase, req);
            } else if ("parser".equals(type) || "ocr".equals(type)) {
                result = testHealth(apiBase, type, req);
            } else {
                result = fail("不支持的测试类型: " + req.getType());
            }
            result.setLatencyMs(System.currentTimeMillis() - start);
            return result;
        } catch (HttpStatusCodeException e) {
            ConnectionTestResult r = fail(classifyHttpError(e));
            r.setLatencyMs(System.currentTimeMillis() - start);
            return r;
        } catch (ResourceAccessException e) {
            ConnectionTestResult r = fail("无法连接服务，请检查 API 地址、网络或超时时间: " + rootMessage(e));
            r.setLatencyMs(System.currentTimeMillis() - start);
            return r;
        } catch (RestClientException e) {
            ConnectionTestResult r = fail("请求失败，请确认接口格式: " + rootMessage(e));
            r.setLatencyMs(System.currentTimeMillis() - start);
            return r;
        } catch (Exception e) {
            ConnectionTestResult r = fail("响应格式错误: " + rootMessage(e));
            r.setLatencyMs(System.currentTimeMillis() - start);
            return r;
        }
    }

    private ConnectionTestResult testEmbedding(String apiBase, ConnectionTestRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.getModel());
        body.put("input", Collections.singletonList("ping"));

        ResponseEntity<Map> resp = restTemplate(req).postForEntity(
                apiBase + "/v1/embeddings", entity(req.getApiKey(), body), Map.class);
        Map bodyMap = resp.getBody();
        if (bodyMap == null) {
            return fail("响应格式错误: 返回体为空");
        }
        Object data = bodyMap.get("data");
        if (data instanceof List && !((List) data).isEmpty()) {
            Object first = ((List) data).get(0);
            if (first instanceof Map && ((Map) first).get("embedding") instanceof List) {
                return ok("连接成功，Embedding 服务返回向量数据");
            }
        }
        if (bodyMap.get("embedding") instanceof List) {
            return ok("连接成功，Embedding 服务返回向量数据");
        }
        return fail("响应格式错误: 缺少 embedding/data 向量字段");
    }

    private ConnectionTestResult testRerank(String apiBase, ConnectionTestRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.getModel());
        body.put("query", "ping");
        body.put("documents", Collections.singletonList("ping test"));

        ResponseEntity<Map> resp = restTemplate(req).postForEntity(
                apiBase + "/v1/rerank", entity(req.getApiKey(), body), Map.class);
        Map bodyMap = resp.getBody();
        if (bodyMap == null) {
            return fail("响应格式错误: 返回体为空");
        }
        if (bodyMap.get("results") instanceof List || bodyMap.get("data") instanceof List
                || bodyMap.get("result") instanceof List || bodyMap.get("scores") instanceof List) {
            return ok("连接成功，Rerank 服务返回排序结果");
        }
        return fail("响应格式错误: 缺少 results/data 排序结果字段");
    }

    private ConnectionTestResult testHealth(String apiBase, String type, ConnectionTestRequest req) throws Exception {
        ResponseEntity<String> resp = restTemplate(req).exchange(
                apiBase + "/health", HttpMethod.GET, new HttpEntity<Void>(new HttpHeaders()), String.class);
        if (resp.getStatusCode().is2xxSuccessful() && isHealthyBody(resp.getBody())) {
            return ok("连接成功，" + type + " 健康检查通过");
        }
        return fail("响应格式错误: 健康检查未返回 UP/ok/200");
    }

    private HttpEntity<Map<String, Object>> entity(String apiKey, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.startsWith(API_KEY_MASK)) {
            headers.set("Authorization", "Bearer " + apiKey.trim());
        }
        return new HttpEntity<>(body, headers);
    }

    private RestTemplate restTemplate(ConnectionTestRequest req) {
        int timeoutSeconds = req == null || req.getTimeoutSeconds() == null ? 15 : req.getTimeoutSeconds();
        if (timeoutSeconds < 1) {
            timeoutSeconds = 1;
        }
        if (timeoutSeconds > 600) {
            timeoutSeconds = 600;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeconds * 1000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        return new RestTemplate(factory);
    }

    private ConnectionTestResult validateConnectionRequest(ConnectionTestRequest req) {
        if (req == null) {
            return fail("请求不能为空");
        }
        if (req.getType() == null || req.getType().trim().isEmpty()) {
            return fail("测试类型不能为空");
        }
        if (req.getApiBase() == null || req.getApiBase().trim().isEmpty()) {
            return fail("API 地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(req.getApiBase().trim());
        } catch (IllegalArgumentException e) {
            return fail("API 地址非法: " + e.getMessage());
        }
        if (uri.getScheme() == null || (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))) {
            return fail("API 地址协议必须为 http/https");
        }
        String type = req.getType().trim().toLowerCase();
        if (("embedding".equals(type) || "rerank".equals(type))
                && (req.getModel() == null || req.getModel().trim().isEmpty())) {
            return fail("模型名称不能为空");
        }
        return null;
    }

    private void validateEmbeddingConfig(EmbeddingConfigDTO dto) {
        requireText(dto == null ? null : dto.getModel(), "embedding.model", 128);
        validateOptionalHttpUrl(dto.getApiBase(), "embedding.api_base");
        validateApiKeyLength(dto.getApiKey(), "embedding.api_key");
        requireRange(dto.getDimension(), "embedding.dimension", 1, 8192);
    }

    private void validateRerankConfig(RerankConfigDTO dto) {
        requireText(dto == null ? null : dto.getModel(), "rerank.model", 128);
        validateOptionalHttpUrl(dto.getApiBase(), "rerank.api_base");
        validateApiKeyLength(dto.getApiKey(), "rerank.api_key");
        requireRange(dto.getTopN(), "rerank.top_n", 1, 100);
        if (dto == null || dto.getThreshold() == null || dto.getThreshold() < 0 || dto.getThreshold() > 1) {
            throw new IllegalArgumentException("rerank.threshold must be between 0 and 1");
        }
    }

    private void validateParserConfig(ParserConfigDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("parser config must not be null");
        }
        validateOptionalHttpUrl(dto.getApiBase(), "parser.api_base");
        requireRange(dto.getChunkSize(), "parser.chunk_size", 100, 5000);
        requireRange(dto.getChunkOverlap(), "parser.chunk_overlap", 0, 1000);
        if (dto.getChunkOverlap() >= dto.getChunkSize()) {
            throw new IllegalArgumentException("parser.chunk_overlap must be less than parser.chunk_size");
        }
        requireRange(dto.getMaxConcurrentTasks(), "parser.max_concurrent_tasks", 1, 20);
        requireRange(dto.getMaxRetryCount(), "parser.max_retry_count", 0, 10);
        requireRange(dto.getTimeoutSeconds(), "parser.timeout_seconds", 1, 600);
    }

    private void fillSavedConnectionFields(ConnectionTestRequest req) {
        if (req == null || req.getType() == null) {
            return;
        }
        String type = req.getType().trim().toLowerCase();
        String prefix;
        if ("embedding".equals(type)) {
            prefix = "embedding";
        } else if ("rerank".equals(type)) {
            prefix = "rerank";
        } else if ("parser".equals(type) || "ocr".equals(type)) {
            prefix = "parser";
        } else {
            return;
        }

        if (isBlank(req.getApiBase())) {
            req.setApiBase(safeGetValue(prefix + ".api_base"));
        }
        if (("embedding".equals(type) || "rerank".equals(type)) && isBlank(req.getModel())) {
            req.setModel(safeGetValue(prefix + ".model"));
        }
        if ("embedding".equals(type) || "rerank".equals(type)) {
            String apiKey = req.getApiKey();
            if (apiKey == null || apiKey.startsWith(API_KEY_MASK)) {
                req.setApiKey(safeGetValue(prefix + ".api_key"));
            }
        }
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " length must be <= " + maxLength);
        }
    }

    private void validateOptionalHttpUrl(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a valid URL");
        }
        if (uri.getScheme() == null || (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException(field + " protocol must be http/https");
        }
    }

    private void validateApiKeyLength(String value, String field) {
        if (value != null && value.length() > 512) {
            throw new IllegalArgumentException(field + " length must be <= 512");
        }
    }

    private void requireRange(Integer value, String field, int min, int max) {
        if (value == null || value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }

    private boolean isHealthyBody(String body) throws Exception {
        if (body == null || body.trim().isEmpty()) {
            return true;
        }
        String text = body.trim();
        if ("ok".equalsIgnoreCase(text) || "up".equalsIgnoreCase(text) || "200".equals(text)) {
            return true;
        }
        if (text.startsWith("{")) {
            Map map = objectMapper.readValue(text, Map.class);
            List<String> keys = new ArrayList<>();
            keys.add("status");
            keys.add("state");
            keys.add("code");
            for (String key : keys) {
                Object value = map.get(key);
                if (value != null) {
                    String v = String.valueOf(value);
                    if ("UP".equalsIgnoreCase(v) || "OK".equalsIgnoreCase(v) || "200".equals(v)) {
                        return true;
                    }
                }
            }
        }
        String lower = text.toLowerCase();
        return lower.contains("\"status\":\"up\"") || lower.contains("\"status\":\"ok\"");
    }

    private String classifyHttpError(HttpStatusCodeException e) {
        int code = e.getRawStatusCode();
        String body = e.getResponseBodyAsString();
        String detail = body == null || body.trim().isEmpty() ? e.getStatusText() : body;
        String lower = detail == null ? "" : detail.toLowerCase();
        if (code == 401 || code == 403) {
            return "API Key 无效或无权限: HTTP " + code;
        }
        if (code == 404 || lower.contains("model not found") || lower.contains("model_not_found")) {
            return "模型名称不存在，请检查模型配置: HTTP " + code;
        }
        if (code == 400 || code == 422) {
            return "请求格式不符合服务要求，请确认接口格式: HTTP " + code + " " + abbreviate(detail);
        }
        return "服务返回错误: HTTP " + code + " " + abbreviate(detail);
    }

    private ConnectionTestResult ok(String message) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.setSuccess(true);
        r.setMessage(message);
        return r;
    }

    private ConnectionTestResult fail(String message) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }

    private String resolveApiKey(String configKey, String apiKey) {
        if (apiKey == null || apiKey.startsWith(API_KEY_MASK)) {
            return safeGetValue(configKey);
        }
        return apiKey;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.getMessage() : t.getMessage();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200) + "...";
    }

    private void updateValue(String key, String value) {
        if (value == null) {
            value = "";
        }
        configMapper.updateConfigValue(key, value);
    }

    private String safeGetValue(String key) {
        String v = configMapper.selectValue(key);
        return v == null ? "" : v;
    }

    private Map<String, String> collectSafeConfigValues() {
        List<Map<String, String>> all = configMapper.selectAllAsMap();
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : mapOf(all).entrySet()) {
            if (e.getKey() != null && e.getKey().endsWith(".api_key")) {
                continue;
            }
            safe.put(e.getKey(), e.getValue());
        }
        return safe;
    }

    private static Map<String, String> mapOf(List<Map<String, String>> rows) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String key = row.get("configKey");
            String value = row.get("configValue");
            if (key != null) {
                m.put(key, value);
            }
        }
        return m;
    }

    private long nextConfigVersion() {
        return System.currentTimeMillis();
    }

    private void writeAudit(String group, Long version, String summary) {
        Operator operator = currentOperator();
        configMapper.insertConfigChangeLog(
                operator.id,
                operator.name,
                group,
                version,
                summary == null ? "" : summary);
    }

    private Operator currentOperator() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return new Operator("system", "system");
        }
        HttpServletRequest request = attrs.getRequest();
        String id = request.getHeader("X-User-Id");
        String name = request.getHeader("X-User-Name");
        return new Operator(isBlank(id) ? "system" : id, isBlank(name) ? "system" : name);
    }

    private void registerAfterCommit(String group, Map<String, String> values, Long version) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            configChangedProducer.publishConfigChanged(group, values, version);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                configChangedProducer.publishConfigChanged(group, values, version);
            }
        });
    }

    private static class Operator {
        private final String id;
        private final String name;

        private Operator(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @SuppressWarnings("unused")
    private static final Class<?> KEEP_IMPORT = SystemConfig.class;
}
