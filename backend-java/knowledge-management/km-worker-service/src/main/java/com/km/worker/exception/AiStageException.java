package com.km.worker.exception;

/**
 * F4 整合（commit #24）：Worker 调用 FastAPI 失败时的阶段化异常。
 *
 * 阶段 errorStage 取值（与文档附录 B 一致）：
 *   STAGING / PARSE / OCR / CHUNK / EMBED / CHROMA / INTERNAL
 *
 * 携带 traceId 用于 Admin 失败事件记录 + Worker 日志关联。
 */
public class AiStageException extends RuntimeException {
    private final String errorStage;
    private final String traceId;

    public AiStageException(String errorStage, String message, String traceId) {
        super(message);
        this.errorStage = errorStage;
        this.traceId = traceId;
    }

    public String getErrorStage() { return errorStage; }
    public String getTraceId() { return traceId; }
}