package com.km.search.dto;

import java.util.ArrayList;
import java.util.List;

public class AiRetrievalResponse {

    private List<AiRetrievalCandidate> candidates = new ArrayList<AiRetrievalCandidate>();
    private Long elapsedMs;

    public List<AiRetrievalCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<AiRetrievalCandidate> candidates) {
        this.candidates = candidates;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}

