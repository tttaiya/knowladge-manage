package com.km.search.dto;

import java.util.ArrayList;
import java.util.List;

public class AiRetrievalResponse {

    private List<AiRetrievalCandidate> candidates =
            new ArrayList<AiRetrievalCandidate>();

    private Long elapsedMs;

    /**
     * FastAPI 是否真正执行了 Rerank。
     *
     * true：执行了 Rerank，可以使用 rerankScore。
     * false：未执行 Rerank，可能已降级为 VECTOR_ONLY。
     */
    private Boolean rerankApplied;

    /**
     * FastAPI 实际降级后的模式。
     *
     * 当前约定：
     * VECTOR_ONLY
     */
    private String degradedMode;

    public List<AiRetrievalCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(
            List<AiRetrievalCandidate> candidates
    ) {
        this.candidates = candidates;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Boolean getRerankApplied() {
        return rerankApplied;
    }

    public void setRerankApplied(Boolean rerankApplied) {
        this.rerankApplied = rerankApplied;
    }

    public String getDegradedMode() {
        return degradedMode;
    }

    public void setDegradedMode(String degradedMode) {
        this.degradedMode = degradedMode;
    }
}