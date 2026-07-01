package com.km.admin;

import org.springframework.stereotype.Component;

/**
 * 切片重向量化 Producer。R14：operatorUserId 改 String UUID。
 * 兼容 review 模块：原传 0L 表示"系统触发"；现在改用 null 表示"无用户上下文"。
 */
@Component
public class ChunkReembedProducer {

    private final TaskCommandService taskCommandService;

    public ChunkReembedProducer(TaskCommandService taskCommandService) {
        this.taskCommandService = taskCommandService;
    }

    public void send(Long chunkId, Long docId, Long kbId, String traceId) {
        send(chunkId, docId, kbId, traceId, null);
    }

    public void send(Long chunkId, Long docId, Long kbId, String traceId, String operatorUserId) {
        send(chunkId, docId, kbId, traceId, operatorUserId, null);
    }

    public void send(Long chunkId, Long docId, Long kbId, String traceId, String operatorUserId, Long chunkContentVersion) {
        taskCommandService.createReembedTask(docId, chunkId, operatorUserId, chunkContentVersion);
    }
}
