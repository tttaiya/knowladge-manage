package com.km.search.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.km.search.config.AiServiceProperties;
import com.km.search.dto.AiRetrievalRequest;
import com.km.search.dto.AiRetrievalResponse;
import com.km.search.exception.BusinessException;
import com.km.search.exception.ErrorCode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class FastApiAiRetrievalClient implements AiRetrievalClient {

    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;
    private final ObjectMapper objectMapper;

    public FastApiAiRetrievalClient(RestTemplate restTemplate,
                                    AiServiceProperties properties,
                                    ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiRetrievalResponse search(AiRetrievalRequest request) {
        String url = buildUrl();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getInternalToken())) {
            headers.set("X-Internal-Token", properties.getInternalToken());
        }

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<AiRetrievalRequest>(request, headers),
                    JsonNode.class
            );
            JsonNode body = response.getBody();
            if (body == null || body.isNull()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务返回空响应");
            }
            JsonNode data = unwrapData(body);
            return objectMapper.convertValue(data, AiRetrievalResponse.class);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException | RestClientException ex) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "调用 AI 检索服务失败：" + ex.getMessage());
        }
    }

    private JsonNode unwrapData(JsonNode body) {
        if (body.has("code") && body.get("code").asInt() != 0) {
            String message = body.has("message") ? body.get("message").asText() : "AI 服务返回错误";
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, message);
        }
        if (body.has("data")) {
            return body.get("data");
        }
        return body;
    }

    private String buildUrl() {
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "未配置 km.ai.base-url");
        }
        String path = properties.getRetrievalPath();
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }
}

