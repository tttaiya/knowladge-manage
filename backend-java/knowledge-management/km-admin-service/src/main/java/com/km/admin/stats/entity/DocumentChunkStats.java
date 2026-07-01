// entity/DocumentChunkStats.java
package com.km.admin.stats.entity;

public class DocumentChunkStats {
    private Long totalChunks;
    private Long pendingVectorCount;
    private Long readyVectorCount;

    public Long getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Long totalChunks) {
        this.totalChunks = totalChunks;
    }

    public Long getPendingVectorCount() {
        return pendingVectorCount;
    }

    public void setPendingVectorCount(Long pendingVectorCount) {
        this.pendingVectorCount = pendingVectorCount;
    }

    public Long getReadyVectorCount() {
        return readyVectorCount;
    }

    public void setReadyVectorCount(Long readyVectorCount) {
        this.readyVectorCount = readyVectorCount;
    }
}
