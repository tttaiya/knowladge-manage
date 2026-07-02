package com.km.report.service.impl;

import com.km.report.common.exception.BizException;
import com.km.report.dto.AiGenerateRequest;
import com.km.report.dto.AiGenerateResponse;
import com.km.report.entity.ReportAiCallLog;
import com.km.report.service.ReportAiCallLogService;
import com.km.report.service.ReportAiService;
import com.km.report.service.ReportSystemConfigService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportAiServiceImpl implements ReportAiService {

    @Resource
    private ReportSystemConfigService reportSystemConfigService;
    @Resource
    private ReportAiCallLogService reportAiCallLogService;

    @Value("${report.llm.api-key:${REPORT_LLM_API_KEY:}}")
    private String configuredApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean enabled() {
        return "1".equals(getKey("report.llm.enabled", "0")) || "true".equalsIgnoreCase(getKey("report.llm.enabled", "false"));
    }

    @Override
    public AiGenerateResponse generate(AiGenerateRequest request) {
        if (!enabled()) {
            throw new BizException("AI 未启用，请先配置 report.llm.enabled");
        }
        if (request == null) {
            throw new BizException("AI 请求不能为空");
        }
        String apiKey = hasText(configuredApiKey) ? configuredApiKey : getKey("report.llm.api-key", "");
        if (!hasText(apiKey)) {
            throw new BizException("AI API Key 未配置");
        }
        String baseUrl = getKey("report.llm.base-url", "https://api.openai.com/v1");
        String model = getKey("report.llm.model", "gpt-4o-mini");
        String url = normalizeUrl(baseUrl);

        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        ReportAiCallLog log = buildLog(request, requestId, model, baseUrl, "RUNNING", null, null, start);
        reportAiCallLogService.save(log);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("temperature", 0.2);
            body.put("stream", Boolean.FALSE);
            body.put("messages", buildMessages(request));
            if (hasText(request.getResponseFormat()) && "json".equalsIgnoreCase(request.getResponseFormat())) {
                Map<String, Object> responseFormat = new HashMap<>();
                responseFormat.put("type", "json_object");
                body.put("response_format", responseFormat);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                throw new BizException("AI 返回为空");
            }

            AiGenerateResponse result = new AiGenerateResponse();
            result.setRawResponse(responseBody.toString());
            result.setModel(model);
            extractAiResult(responseBody, result);
            if (!hasText(result.getContent())) {
                result.setContent(responseBody.toString());
            }
            fillUsage(responseBody, result);
            finishLog(log, result, "SUCCESS", null, start, summarizeResponse(result));
            return result;
        } catch (RestClientException ex) {
            finishLog(log, null, "FAILED", ex.getMessage(), start, null);
            throw new BizException("AI 调用失败：" + ex.getMessage());
        } catch (RuntimeException ex) {
            finishLog(log, null, "FAILED", ex.getMessage(), start, null);
            throw ex;
        }
    }

    private List<Map<String, String>> buildMessages(AiGenerateRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", request.getSystemPrompt()));
        messages.add(message("user", request.getUserPrompt()));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private void extractAiResult(Map<?, ?> responseBody, AiGenerateResponse result) {
        Object choicesObj = responseBody.get("choices");
        if (choicesObj instanceof List && !((List<?>) choicesObj).isEmpty()) {
            Object first = ((List<?>) choicesObj).get(0);
            if (first instanceof Map) {
                Map<?, ?> firstMap = (Map<?, ?>) first;
                Object messageObj = firstMap.get("message");
                if (messageObj instanceof Map) {
                    Object contentObj = ((Map<?, ?>) messageObj).get("content");
                    if (contentObj != null) {
                        result.setContent(String.valueOf(contentObj));
                    }
                }
                Object finishReason = firstMap.get("finish_reason");
                if (finishReason != null) {
                    result.setFinishReason(String.valueOf(finishReason));
                }
            }
        }
    }

    private void fillUsage(Map<?, ?> responseBody, AiGenerateResponse result) {
        Object usageObj = responseBody.get("usage");
        if (usageObj instanceof Map) {
            Map<?, ?> usage = (Map<?, ?>) usageObj;
            result.setPromptTokens(toInteger(usage.get("prompt_tokens")));
            result.setCompletionTokens(toInteger(usage.get("completion_tokens")));
            result.setTotalTokens(toInteger(usage.get("total_tokens")));
        }
    }

    private ReportAiCallLog buildLog(AiGenerateRequest request, String requestId, String model, String baseUrl, String status, String errorMsg, String responseBody, long start) {
        ReportAiCallLog log = new ReportAiCallLog();
        log.setRequestId(requestId);
        log.setReportId(request.getReportId());
        log.setChapterId(request.getChapterId());
        log.setCallType(hasText(request.getResponseFormat()) && "json".equalsIgnoreCase(request.getResponseFormat()) ? "OUTLINE" : "CHAPTER");
        log.setModelName(model);
        log.setStatus("SUCCESS".equals(status) ? 1 : 0);
        log.setErrorMsg(errorMsg);
        log.setRequestBody(buildRequestBody(baseUrl, request));
        log.setResponseBody(responseBody);
        log.setCreateTime(LocalDateTime.now());
        log.setFinishTime(LocalDateTime.now());
        log.setDurationMs(0L);
        return log;
    }

    private void finishLog(ReportAiCallLog log, AiGenerateResponse result, String status, String errorMsg, long start, String responseBody) {
        if (log == null) {
            return;
        }
        log.setStatus("SUCCESS".equals(status) ? 1 : 0);
        log.setErrorMsg(errorMsg);
        log.setResponseBody(responseBody);
        log.setDurationMs(System.currentTimeMillis() - start);
        log.setFinishTime(LocalDateTime.now());
        if (result != null) {
            log.setPromptTokens(result.getPromptTokens());
            log.setCompletionTokens(result.getCompletionTokens());
            log.setTotalTokens(result.getTotalTokens());
        }
        reportAiCallLogService.updateById(log);
    }

    private String buildRequestBody(String baseUrl, AiGenerateRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("baseUrl=").append(baseUrl).append('\n');
        builder.append("reportId=").append(request.getReportId()).append('\n');
        builder.append("chapterId=").append(request.getChapterId()).append('\n');
        builder.append("responseFormat=").append(request.getResponseFormat()).append('\n');
        builder.append("systemPromptChars=").append(length(request.getSystemPrompt())).append('\n');
        builder.append("userPromptChars=").append(length(request.getUserPrompt())).append('\n');
        return builder.toString();
    }

    private String summarizeResponse(AiGenerateResponse result) {
        if (result == null) {
            return "";
        }
        return "contentChars=" + length(result.getContent())
                + "\nfinishReason=" + (result.getFinishReason() == null ? "" : result.getFinishReason())
                + "\npromptTokens=" + result.getPromptTokens()
                + "\ncompletionTokens=" + result.getCompletionTokens()
                + "\ntotalTokens=" + result.getTotalTokens();
    }

    private String normalizeUrl(String baseUrl) {
        if (!hasText(baseUrl)) {
            return "https://api.openai.com/v1/chat/completions";
        }
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            return normalized + "chat/completions";
        }
        return normalized + "/chat/completions";
    }

    private String getKey(String key, String defaultValue) {
        return reportSystemConfigService.getValueByKey(key, defaultValue);
    }

    private boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }
}
