package com.km.search.dto;

import java.util.ArrayList;
import java.util.List;

public class RetrievalSearchResponse {

    private List<RetrievalResultItem> records = new ArrayList<RetrievalResultItem>();
    private int total;
    private long elapsedMs;
    private String effectiveMode;
    private String modeSource;
    private String degradedMode;

    public List<RetrievalResultItem> getRecords() {
        return records;
    }

    public void setRecords(List<RetrievalResultItem> records) {
        this.records = records;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getEffectiveMode() {
        return effectiveMode;
    }

    public void setEffectiveMode(String effectiveMode) {
        this.effectiveMode = effectiveMode;
    }

    public String getModeSource() {
        return modeSource;
    }

    public void setModeSource(String modeSource) {
        this.modeSource = modeSource;
    }

    public String getDegradedMode() {
        return degradedMode;
    }

    public void setDegradedMode(String degradedMode) {
        this.degradedMode = degradedMode;
    }
}

