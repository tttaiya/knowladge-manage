package com.km.worker.limits;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * F4 整合（commit #24）：心跳句柄（抽出独立文件）。
 */
public class HeartbeatHandle {
    public final Long taskId;
    public final String claimToken;
    public final AtomicBoolean leaseLost = new AtomicBoolean(false);
    public ScheduledFuture<?> future;

    public HeartbeatHandle(Long taskId, String claimToken) {
        this.taskId = taskId;
        this.claimToken = claimToken;
    }

    public void stop() {
        if (future != null) {
            future.cancel(true);
        }
    }
}