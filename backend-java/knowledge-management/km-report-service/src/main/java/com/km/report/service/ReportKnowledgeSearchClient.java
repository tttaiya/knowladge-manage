package com.km.report.service;

import com.km.report.common.context.LoginUserContext;
import com.km.report.common.exception.BizException;
import com.km.report.dto.KnowledgeSearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportKnowledgeSearchClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${report.search.base-url:${KM_SEARCH_BASE_URL:http://km-search-service:9103}}")
    private String baseUrl;

    @Value("${report.search.internal-token:${INTERNAL_TOKEN:demo-internal-token}}")
    private String internalToken;

    public List<KnowledgeSearchHit> search(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            return new ArrayList<KnowledgeSearchHit>();
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("query", query);
        body.put("topK", topK);
        body.put("mode", "VECTOR_RERANK");
        body.put("userId", LoginUserContext.getUserId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(internalToken)) {
            headers.set("X-Internal-Token", internalToken);
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    normalizeBaseUrl() + "/internal/v1/retrieval/search",
                    HttpMethod.POST,
                    new HttpEntity<Map<String, Object>>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return parseRecords(response.getBody());
        } catch (RestClientException ex) {
            throw new BizException("知识检索服务调用失败：" + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<KnowledgeSearchHit> parseRecords(Map<String, Object> envelope) {
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        if (envelope == null || !"0".equals(String.valueOf(envelope.get("code")))) {
            return hits;
        }
        Object dataObj = envelope.get("data");
        if (!(dataObj instanceof Map)) {
            return hits;
        }
        Object recordsObj = ((Map<String, Object>) dataObj).get("records");
        if (!(recordsObj instanceof List)) {
            return hits;
        }
        for (Object recordObj : (List<?>) recordsObj) {
            if (!(recordObj instanceof Map)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) recordObj;
            KnowledgeSearchHit hit = new KnowledgeSearchHit();
            hit.setChunkId(asLong(row.get("chunkId")));
            hit.setDocId(asLong(row.get("docId")));
            hit.setDocName(asString(row.get("docName")));
            hit.setKbId(asLong(row.get("kbId")));
            hit.setKbName(asString(row.get("kbName")));
            hit.setChapterPath(asString(row.get("chapterPath")));
            hit.setPageNo(asInteger(row.get("pageNo")));
            hit.setVectorId(asString(row.get("vectorId")));
            hit.setContent(asString(row.get("content")));
            hit.setSummary(asString(row.get("summary")));
            hit.setSimilarityScore(asDouble(row.get("similarityScore")));
            hit.setRerankScore(asDouble(row.get("rerankScore")));
            hits.add(hit);
        }
        return hits;
    }

    private String normalizeBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }
}
