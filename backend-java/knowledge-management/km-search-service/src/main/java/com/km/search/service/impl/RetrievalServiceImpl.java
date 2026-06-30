package com.km.search.service.impl;

import com.km.search.client.AiRetrievalClient;
import com.km.search.config.RetrievalDefaultsProperties;
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

    public RetrievalServiceImpl(RetrievalMapper retrievalMapper,
                                AiRetrievalClient aiRetrievalClient,
                                RetrievalDefaultsProperties defaults) {
        this.retrievalMapper = retrievalMapper;
        this.aiRetrievalClient = aiRetrievalClient;
        this.defaults = defaults;
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
            return emptyResponse(begin);
        }

        AiRetrievalRequest aiRequest = buildAiRequest(normalized, readyDocIds);
        AiRetrievalResponse aiResponse = aiRetrievalClient.search(aiRequest);
        List<AiRetrievalCandidate> candidates = normalizeCandidates(aiResponse, normalized);
        if (candidates.isEmpty()) {
            return emptyResponse(begin);
        }

        List<Long> chunkIds = candidates.stream()
                .map(AiRetrievalCandidate::getChunkId)
                .collect(Collectors.toList());
        Map<Long, ChunkDetailRecord> chunkMap = retrievalMapper.selectChunkDetailsByIds(chunkIds)
                .stream()
                .collect(Collectors.toMap(ChunkDetailRecord::getChunkId, item -> item));
        if (chunkMap.isEmpty()) {
            return emptyResponse(begin);
        }

        Map<Long, List<String>> tagMap = loadTags(chunkMap.values().stream()
                .map(ChunkDetailRecord::getDocId)
                .collect(Collectors.toList()));

        List<RetrievalResultItem> records = new ArrayList<RetrievalResultItem>();
        for (AiRetrievalCandidate candidate : candidates) {
            ChunkDetailRecord detail = chunkMap.get(candidate.getChunkId());
            if (detail == null) {
                continue;
            }
            records.add(toResultItem(detail, candidate, tagMap, normalized.query, begin));
            if (records.size() >= normalized.topK) {
                break;
            }
        }

        RetrievalSearchResponse response = new RetrievalSearchResponse();
        response.setRecords(records);
        response.setTotal(records.size());
        response.setElapsedMs(System.currentTimeMillis() - begin);
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

        RetrievalMode mode = RetrievalMode.from(StringUtils.hasText(request.getMode())
                ? request.getMode()
                : defaults.getDefaultMode());
        int topK = request.getTopK() == null ? defaults.getDefaultTopK() : request.getTopK();
        if (topK <= 0 || topK > defaults.getMaxTopK()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "topK 范围为 1～" + defaults.getMaxTopK());
        }

        double similarityThreshold = request.getSimilarityThreshold() == null
                ? defaults.getDefaultSimilarityThreshold()
                : request.getSimilarityThreshold();
        assertThreshold("similarityThreshold", similarityThreshold);

        int rerankTopN = request.getRerankTopN() == null ? defaults.getDefaultRerankTopN() : request.getRerankTopN();
        if (rerankTopN <= 0 || rerankTopN > defaults.getMaxTopK()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "rerankTopN 范围为 1～" + defaults.getMaxTopK());
        }

        double rerankThreshold = request.getRerankThreshold() == null
                ? defaults.getDefaultRerankThreshold()
                : request.getRerankThreshold();
        assertThreshold("rerankThreshold", rerankThreshold);

        NormalizedRequest normalized = new NormalizedRequest();
        normalized.query = query;
        normalized.knowledgeBaseIds = normalizeLongList(request.getKnowledgeBaseIds());
        normalized.tags = normalizeStringList(request.getTags());
        normalized.mode = mode;
        normalized.topK = topK;
        normalized.similarityThreshold = similarityThreshold;
        normalized.rerankTopN = rerankTopN;
        normalized.rerankThreshold = rerankThreshold;
        return normalized;
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
        aiRequest.setRerankTopN(normalized.rerankTopN);
        aiRequest.setRerankThreshold(normalized.rerankThreshold);
        return aiRequest;
    }

    private List<AiRetrievalCandidate> normalizeCandidates(AiRetrievalResponse response, NormalizedRequest request) {
        if (response == null || response.getCandidates() == null) {
            return Collections.emptyList();
        }
        List<AiRetrievalCandidate> candidates = new ArrayList<AiRetrievalCandidate>();
        for (AiRetrievalCandidate candidate : response.getCandidates()) {
            if (candidate == null || candidate.getChunkId() == null) {
                continue;
            }
            double similarity = candidate.normalizedSimilarity();
            if (similarity < request.similarityThreshold) {
                continue;
            }
            candidate.setSimilarityScore(similarity);
            if (request.mode == RetrievalMode.VECTOR_RERANK) {
                if (candidate.getRerankScore() == null || candidate.getRerankScore() < request.rerankThreshold) {
                    continue;
                }
            }
            candidates.add(candidate);
        }

        candidates.sort(new Comparator<AiRetrievalCandidate>() {
            @Override
            public int compare(AiRetrievalCandidate left, AiRetrievalCandidate right) {
                if (request.mode == RetrievalMode.VECTOR_RERANK) {
                    int rerankCompare = compareNullableDoubleDesc(left.getRerankScore(), right.getRerankScore());
                    if (rerankCompare != 0) {
                        return rerankCompare;
                    }
                }
                return Double.compare(right.normalizedSimilarity(), left.normalizedSimilarity());
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
                                             long begin) {
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
        item.setRerankScore(candidate.getRerankScore());
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

    private RetrievalSearchResponse emptyResponse(long begin) {
        RetrievalSearchResponse response = new RetrievalSearchResponse();
        response.setRecords(Collections.<RetrievalResultItem>emptyList());
        response.setTotal(0);
        response.setElapsedMs(System.currentTimeMillis() - begin);
        return response;
    }

    private static class NormalizedRequest {
        private String query;
        private List<Long> knowledgeBaseIds;
        private List<String> tags;
        private RetrievalMode mode;
        private int topK;
        private double similarityThreshold;
        private int rerankTopN;
        private double rerankThreshold;
    }
}

