package com.km.search.service.impl;

import com.km.search.client.AiRetrievalClient;
import com.km.search.config.RetrievalDefaultsProperties;
import com.km.search.config.SearchDynamicConfigHolder;
import com.km.search.dto.AiRetrievalCandidate;
import com.km.search.dto.AiRetrievalRequest;
import com.km.search.dto.AiRetrievalResponse;
import com.km.search.dto.ChunkDetailRecord;
import com.km.search.dto.ChunkDetailResponse;
import com.km.search.dto.DocTagRow;
import com.km.search.dto.RetrievalResultItem;
import com.km.search.dto.RetrievalSearchRequest;
import com.km.search.dto.RetrievalSearchResponse;
import com.km.search.exception.BusinessException;
import com.km.search.exception.ErrorCode;
import com.km.search.mapper.RetrievalMapper;
import com.km.search.model.RetrievalMode;
import com.km.search.service.RetrievalService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private final RetrievalMapper retrievalMapper;
    private final AiRetrievalClient aiRetrievalClient;
    private final RetrievalDefaultsProperties defaults;
    private final SearchDynamicConfigHolder dynamicConfigHolder;

    public RetrievalServiceImpl(RetrievalMapper retrievalMapper,
                                AiRetrievalClient aiRetrievalClient,
                                RetrievalDefaultsProperties defaults,
                                SearchDynamicConfigHolder dynamicConfigHolder) {
        this.retrievalMapper = retrievalMapper;
        this.aiRetrievalClient = aiRetrievalClient;
        this.defaults = defaults;
        this.dynamicConfigHolder = dynamicConfigHolder;
    }

    @Override
    public RetrievalSearchResponse search(RetrievalSearchRequest request) {
        long begin = System.currentTimeMillis();
        NormalizedRequest normalized = normalize(request);

        List<Long> readyDocIds = retrievalMapper.selectReadyDocIds(
                normalized.knowledgeBaseIds,
                normalized.tags,
                normalized.tags.size()
        );
        if (readyDocIds == null || readyDocIds.isEmpty()) {
            return emptyResponse(begin, normalized, null);
        }

        AiRetrievalRequest aiRequest = buildAiRequest(normalized, readyDocIds);
        AiRetrievalResponse aiResponse = aiRetrievalClient.search(aiRequest);
        RetrievalMode actualMode = resolveActualMode(aiResponse, normalized.mode);
        List<AiRetrievalCandidate> candidates =
                normalizeCandidates(aiResponse, normalized, actualMode);
        if (candidates.isEmpty()) {
            return emptyResponse(begin, normalized, aiResponse);
        }

        List<String> vectorIds = candidates.stream()
                .map(AiRetrievalCandidate::getVectorId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (vectorIds.isEmpty()) {
            return emptyResponse(begin, normalized, aiResponse);
        }

        List<ChunkDetailRecord> chunkDetails =
                retrievalMapper.selectChunkDetailsByVectorIds(vectorIds);
        Map<String, ChunkDetailRecord> chunkMap =
                new LinkedHashMap<String, ChunkDetailRecord>();
        if (chunkDetails != null) {
            for (ChunkDetailRecord detail : chunkDetails) {
                if (detail != null && StringUtils.hasText(detail.getVectorId())) {
                    chunkMap.put(detail.getVectorId(), detail);
                }
            }
        }
        if (chunkMap.isEmpty()) {
            return emptyResponse(begin, normalized, aiResponse);
        }

        Map<Long, List<String>> tagMap = loadTags(chunkMap.values().stream()
                .map(ChunkDetailRecord::getDocId)
                .collect(Collectors.toList()));

        List<RetrievalResultItem> records = new ArrayList<RetrievalResultItem>();
        for (AiRetrievalCandidate candidate : candidates) {
            ChunkDetailRecord detail = chunkMap.get(candidate.getVectorId());
            if (detail == null) {
                continue;
            }
            records.add(toResultItem(detail, candidate, tagMap, normalized.query, begin, actualMode));
            if (records.size() >= normalized.topK) {
                break;
            }
        }

        RetrievalSearchResponse response = new RetrievalSearchResponse();
        response.setRecords(records);
        response.setTotal(records.size());
        response.setElapsedMs(System.currentTimeMillis() - begin);
        fillPolicyFields(response, normalized, aiResponse);
        return response;
    }

    @Override
    public ChunkDetailResponse getChunkDetail(Long chunkId) {
        if (chunkId == null || chunkId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "chunkId 必须为正整数");
        }
        ChunkDetailRecord record = retrievalMapper.selectChunkDetail(chunkId);
        if (record == null) {
            throw new BusinessException(ErrorCode.CHUNK_NOT_FOUND);
        }
        Map<Long, List<String>> tagMap = loadTags(Collections.singletonList(record.getDocId()));
        ChunkDetailResponse response = new ChunkDetailResponse();
        response.setChunkId(record.getChunkId());
        response.setDocId(record.getDocId());
        response.setDocName(record.getDocName());
        response.setKbId(record.getKbId());
        response.setKbName(record.getKbName());
        response.setChapterPath(record.getChapterPath());
        response.setPageNo(record.getPageNo());
        response.setChunkType(record.getChunkType());
        response.setContent(record.getContent());
        response.setVectorId(record.getVectorId());
        response.setTags(tagMap.getOrDefault(record.getDocId(), Collections.<String>emptyList()));
        return response;
    }

    private NormalizedRequest normalize(RetrievalSearchRequest request) {
        if (request == null) {
            request = new RetrievalSearchRequest();
        }
        String query = request.getQuery() == null ? "" : request.getQuery().trim();
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "query 不能为空");
        }

        RetrievalMode requestMode = StringUtils.hasText(request.getMode())
                ? RetrievalMode.from(request.getMode())
                : null;
        int topK = request.getTopK() == null ? defaults.getDefaultTopK() : request.getTopK();
        if (topK <= 0 || topK > defaults.getMaxTopK()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "topK 范围为 1～" + defaults.getMaxTopK());
        }

        double similarityThreshold = request.getSimilarityThreshold() == null
                ? defaults.getDefaultSimilarityThreshold()
                : request.getSimilarityThreshold();
        assertThreshold("similarityThreshold", similarityThreshold);

        int defaultRerankTopN = dynamicConfigHolder.getRerankTopN() == null
                ? defaults.getDefaultRerankTopN()
                : dynamicConfigHolder.getRerankTopN();
        int rerankTopN = request.getRerankTopN() == null ? defaultRerankTopN : request.getRerankTopN();
        if (rerankTopN <= 0 || rerankTopN > defaults.getMaxTopK()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "rerankTopN 范围为 1～" + defaults.getMaxTopK());
        }

        double defaultRerankThreshold = dynamicConfigHolder.getRerankThreshold() == null
                ? defaults.getDefaultRerankThreshold()
                : dynamicConfigHolder.getRerankThreshold();
        double rerankThreshold = request.getRerankThreshold() == null ? defaultRerankThreshold : request.getRerankThreshold();
        assertThreshold("rerankThreshold", rerankThreshold);

        NormalizedRequest normalized = new NormalizedRequest();
        normalized.query = query;
        normalized.knowledgeBaseIds = normalizeLongList(request.getKnowledgeBaseIds());
        normalized.tags = normalizeStringList(request.getTags());
        EffectivePolicy policy = resolveEffectivePolicy(normalized.knowledgeBaseIds, requestMode);
        normalized.mode = policy.mode;
        normalized.modeSource = policy.source;
        normalized.topK = topK;
        normalized.similarityThreshold = similarityThreshold;
        normalized.rerankTopN = rerankTopN;
        normalized.rerankThreshold = rerankThreshold;
        return normalized;
    }

    private EffectivePolicy resolveEffectivePolicy(List<Long> knowledgeBaseIds, RetrievalMode requestMode) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return new EffectivePolicy(
                    requestMode == null ? RetrievalMode.from(defaults.getDefaultMode()) : requestMode,
                    requestMode == null ? "DEFAULT" : "REQUEST");
        }

        List<Map<String, Object>> rows = retrievalMapper.selectKnowledgeBasePolicies(knowledgeBaseIds);
        if (rows == null || rows.size() != knowledgeBaseIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "所选知识库不存在或已删除");
        }

        if (knowledgeBaseIds.size() == 1) {
            return new EffectivePolicy(modeFromPolicy(rows.get(0)), "KNOWLEDGE_BASE");
        }

        if (requestMode != null) {
            return new EffectivePolicy(requestMode, "REQUEST");
        }

        RetrievalMode commonMode = null;
        for (Map<String, Object> row : rows) {
            RetrievalMode rowMode = modeFromPolicy(row);
            if (commonMode == null) {
                commonMode = rowMode;
            } else if (commonMode != rowMode) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        "所选知识库检索策略不一致，请选择检索模式");
            }
        }
        return new EffectivePolicy(commonMode, "MULTI_KB_CONSENSUS");
    }

    private RetrievalMode modeFromPolicy(Map<String, Object> row) {
        Object value = row == null ? null : row.get("retrievalStrategy");
        return RetrievalMode.from(value == null ? null : String.valueOf(value));
    }

    private void assertThreshold(String field, double value) {
        if (value < 0.0D || value > 1.0D) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, field + " 范围为 0～1");
        }
    }

    private List<Long> normalizeLongList(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> result = new LinkedHashSet<Long>();
        for (Long value : values) {
            if (value != null && value > 0) {
                result.add(value);
            }
        }
        return new ArrayList<Long>(result);
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return new ArrayList<String>(result);
    }

    private AiRetrievalRequest buildAiRequest(NormalizedRequest normalized, List<Long> readyDocIds) {
        int candidateK = Math.min(defaults.getMaxCandidateK(), Math.max(normalized.topK, normalized.topK * 10));
        AiRetrievalRequest aiRequest = new AiRetrievalRequest();
        aiRequest.setQuery(normalized.query);
        aiRequest.setKnowledgeBaseIds(normalized.knowledgeBaseIds);
        aiRequest.setDocIds(readyDocIds);
        aiRequest.setTags(normalized.tags);
        aiRequest.setMode(normalized.mode.name());
        aiRequest.setTopK(normalized.topK);
        aiRequest.setCandidateK(candidateK);
        aiRequest.setSimilarityThreshold(normalized.similarityThreshold);
        aiRequest.setEmbeddingModel(dynamicConfigHolder.getEmbeddingModel());
        aiRequest.setEmbeddingApiBase(dynamicConfigHolder.getEmbeddingApiBase());
        aiRequest.setRerankModel(dynamicConfigHolder.getRerankModel());
        aiRequest.setRerankApiBase(dynamicConfigHolder.getRerankApiBase());
        aiRequest.setRerankTopN(normalized.rerankTopN);
        aiRequest.setRerankThreshold(normalized.rerankThreshold);
        return aiRequest;
    }

    private RetrievalMode resolveActualMode(AiRetrievalResponse response,
                                                      RetrievalMode requestedMode) {
        if (requestedMode == RetrievalMode.VECTOR_RERANK
                && response != null
                && "VECTOR_ONLY".equalsIgnoreCase(response.getDegradedMode())) {
            return RetrievalMode.VECTOR_ONLY;
        }
        return requestedMode;
    }

    private List<AiRetrievalCandidate> normalizeCandidates(
            AiRetrievalResponse response,
            NormalizedRequest request,
            final RetrievalMode actualMode) {
        if (response == null || response.getCandidates() == null) {
            return Collections.emptyList();
        }

        List<AiRetrievalCandidate> candidates = new ArrayList<AiRetrievalCandidate>();
        for (AiRetrievalCandidate candidate : response.getCandidates()) {
            if (candidate == null || !StringUtils.hasText(candidate.getVectorId())) {
                continue;
            }

            double similarity = candidate.normalizedSimilarity();
            if (similarity < request.similarityThreshold) {
                continue;
            }
            candidate.setSimilarityScore(similarity);

            if (actualMode == RetrievalMode.VECTOR_RERANK
                    && (candidate.getRerankScore() == null
                    || candidate.getRerankScore() < request.rerankThreshold)) {
                continue;
            }
            candidates.add(candidate);
        }

        candidates.sort(new Comparator<AiRetrievalCandidate>() {
            @Override
            public int compare(AiRetrievalCandidate left, AiRetrievalCandidate right) {
                if (actualMode == RetrievalMode.VECTOR_RERANK) {
                    int rerankCompare = compareNullableDoubleDesc(
                            left.getRerankScore(), right.getRerankScore());
                    if (rerankCompare != 0) {
                        return rerankCompare;
                    }
                }
                return Double.compare(
                        right.normalizedSimilarity(), left.normalizedSimilarity());
            }
        });
        return candidates;
    }

    private int compareNullableDoubleDesc(Double left, Double right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Double.compare(right, left);
    }

    private Map<Long, List<String>> loadTags(List<Long> docIds) {
        List<Long> normalizedDocIds = normalizeLongList(docIds);
        if (normalizedDocIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<String>> result = new LinkedHashMap<Long, List<String>>();
        for (DocTagRow row : retrievalMapper.selectTagsByDocIds(normalizedDocIds)) {
            result.computeIfAbsent(row.getDocId(), k -> new ArrayList<String>()).add(row.getTagName());
        }
        return result;
    }

    private RetrievalResultItem toResultItem(ChunkDetailRecord detail,
                                             AiRetrievalCandidate candidate,
                                             Map<Long, List<String>> tagMap,
                                             String query,
                                             long begin,
                                             RetrievalMode actualMode) {
        RetrievalResultItem item = new RetrievalResultItem();
        item.setChunkId(detail.getChunkId());
        item.setDocId(detail.getDocId());
        item.setDocName(detail.getDocName());
        item.setKbId(detail.getKbId());
        item.setKbName(detail.getKbName());
        item.setChapterPath(detail.getChapterPath());
        item.setPageNo(detail.getPageNo());
        item.setChunkType(detail.getChunkType());
        item.setContent(detail.getContent());
        item.setSummary(buildSummary(detail.getContent(), query, 180));
        item.setSimilarityScore(candidate.normalizedSimilarity());
        item.setRerankScore(actualMode == RetrievalMode.VECTOR_RERANK
                ? candidate.getRerankScore()
                : null);
        item.setTags(tagMap.getOrDefault(detail.getDocId(), Collections.<String>emptyList()));
        item.setElapsedMs(System.currentTimeMillis() - begin);
        return item;
    }

    private String buildSummary(String content, String query, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        int start = 0;
        if (StringUtils.hasText(query)) {
            int index = normalized.indexOf(query);
            if (index > 0) {
                start = Math.max(0, index - 40);
            }
        }
        int end = Math.min(normalized.length(), start + maxLength);
        String summary = normalized.substring(start, end);
        if (start > 0) {
            summary = "..." + summary;
        }
        if (end < normalized.length()) {
            summary = summary + "...";
        }
        return summary;
    }

    private RetrievalSearchResponse emptyResponse(long begin, NormalizedRequest normalized, AiRetrievalResponse aiResponse) {
        RetrievalSearchResponse response = new RetrievalSearchResponse();
        response.setRecords(Collections.<RetrievalResultItem>emptyList());
        response.setTotal(0);
        response.setElapsedMs(System.currentTimeMillis() - begin);
        fillPolicyFields(response, normalized, aiResponse);
        return response;
    }

    private void fillPolicyFields(RetrievalSearchResponse response,
                                  NormalizedRequest normalized,
                                  AiRetrievalResponse aiResponse) {
        response.setEffectiveMode(normalized.mode == null ? null : normalized.mode.name());
        response.setModeSource(normalized.modeSource);
        response.setDegradedMode(aiResponse == null ? null : aiResponse.getDegradedMode());
    }

    private static class EffectivePolicy {
        private final RetrievalMode mode;
        private final String source;

        private EffectivePolicy(RetrievalMode mode, String source) {
            this.mode = mode;
            this.source = source;
        }
    }

    private static class NormalizedRequest {
        private String query;
        private List<Long> knowledgeBaseIds;
        private List<String> tags;
        private RetrievalMode mode;
        private String modeSource;
        private int topK;
        private double similarityThreshold;
        private int rerankTopN;
        private double rerankThreshold;
    }
}

