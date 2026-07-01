package com.km.admin;

import org.springframework.stereotype.Component;

/**
 * 人工编辑切片后创建 REEMBED 任务。
 */
@Component
public class ChunkReembedProducer {

    private final TaskCommandService taskCommandService;

    public ChunkReembedProducer(
            TaskCommandService taskCommandService
    ) {
        this.taskCommandService = taskCommandService;
    }

    public void send(
            Long chunkId,
            Long docId,
            Long kbId,
            Long contentVersion
    ) {
        send(chunkId, docId, kbId, null, contentVersion);
    }

    public void send(
            Long chunkId,
            Long docId,
            Long kbId,
            String operatorUserId,
            Long contentVersion
    ) {
        if (chunkId == null) {
            throw new IllegalArgumentException(
                    "chunkId 不能为空"
            );
        }

        if (docId == null) {
            throw new IllegalArgumentException(
                    "docId 不能为空"
            );
        }

        if (contentVersion == null
                || contentVersion < 1) {
            throw new IllegalArgumentException(
                    "contentVersion 必须大于等于 1"
            );
        }

        /*
         * kbId 由 TaskCommandService 根据 docId、
         * chunkId 从数据库读取并校验。
         */
        taskCommandService.createReembedTask(
                docId,
                chunkId,
                operatorUserId,
                contentVersion
        );
    }
}
