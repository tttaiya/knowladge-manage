package com.km.search.controller;

import com.km.search.common.ApiResponse;
import com.km.search.dto.RetrievalSearchRequest;
import com.km.search.dto.RetrievalSearchResponse;
import com.km.search.service.RetrievalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retrieval")
public class RetrievalController {

    private final RetrievalService retrievalService;

    public RetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/search")
    public ApiResponse<RetrievalSearchResponse> search(@RequestBody(required = false) RetrievalSearchRequest request) {
        return ApiResponse.success(retrievalService.search(request));
    }
}

