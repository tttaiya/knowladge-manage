package com.km.search.controller;

import com.km.search.common.ApiResponse;
import com.km.search.config.InternalTokenProperties;
import com.km.search.dto.ChunkDetailResponse;
import com.km.search.dto.RetrievalSearchRequest;
import com.km.search.dto.RetrievalSearchResponse;
import com.km.search.exception.BusinessException;
import com.km.search.exception.ErrorCode;
import com.km.search.service.RetrievalService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/retrieval")
public class InternalRetrievalController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RetrievalService retrievalService;
    private final InternalTokenProperties tokenProperties;

    public InternalRetrievalController(RetrievalService retrievalService,
                                       InternalTokenProperties tokenProperties) {
        this.retrievalService = retrievalService;
        this.tokenProperties = tokenProperties;
    }

    @PostMapping("/search")
    public ApiResponse<RetrievalSearchResponse> internalSearch(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) RetrievalSearchRequest request) {
        verifyToken(token);
        return ApiResponse.success(retrievalService.search(request));
    }

    @GetMapping("/chunks/{chunkId}")
    public ApiResponse<ChunkDetailResponse> getChunk(
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String token,
            @PathVariable("chunkId") Long chunkId) {
        verifyToken(token);
        return ApiResponse.success(retrievalService.getChunkDetail(chunkId));
    }

    private void verifyToken(String token) {
        String expected = tokenProperties.getInternalToken();
        if (!StringUtils.hasText(expected) || !expected.equals(token)) {
            throw new BusinessException(ErrorCode.INTERNAL_TOKEN_INVALID);
        }
    }
}

