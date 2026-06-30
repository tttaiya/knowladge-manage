package com.km.worker.messaging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * F4 整合（commit #24）：任务事件序号自增器（抽出独立文件）。
 */
public class EventSeq {
    private static final Map<Long, Integer> SEQ = new ConcurrentHashMap<>();

    public static int next(Long taskId) {
        return SEQ.merge(taskId, 1, Integer::sum);
    }
}