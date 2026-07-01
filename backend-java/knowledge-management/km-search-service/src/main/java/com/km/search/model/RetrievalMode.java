package com.km.search.model;

import com.km.search.exception.BusinessException;
import com.km.search.exception.ErrorCode;

public enum RetrievalMode {

    /**
     * 语义检索兼容模式。
     */
    SEMANTIC,

    /**
     * 仅使用向量相似度，不要求 rerankScore。
     *
     * FastAPI 在 Rerank 调用失败时，
     * 会通过 degradedMode=VECTOR_ONLY 表示降级到该模式。
     */
    VECTOR_ONLY,

    /**
     * 向量召回后再执行 Rerank。
     */
    VECTOR_RERANK;

    public static RetrievalMode from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return VECTOR_RERANK;
        }

        try {
            return RetrievalMode.valueOf(
                    value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "mode 只能为 SEMANTIC、"
                            + "VECTOR_ONLY 或 VECTOR_RERANK"
            );
        }
    }
}