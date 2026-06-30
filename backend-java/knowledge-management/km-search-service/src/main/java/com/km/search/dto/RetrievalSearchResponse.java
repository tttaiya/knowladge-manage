package com.km.search.dto;

import java.util.ArrayList;
import java.util.List;

public class RetrievalSearchResponse {

    private List<RetrievalResultItem> records = new ArrayList<RetrievalResultItem>();
    private int total;
    private long elapsedMs;

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
}

