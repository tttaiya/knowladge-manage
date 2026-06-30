package com.km.search.model;

import com.km.search.exception.BusinessException;
import com.km.search.exception.ErrorCode;

public enum RetrievalMode {
    SEMANTIC,
    VECTOR_RERANK;

    public static RetrievalMode from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return VECTOR_RERANK;
        }
        try {
            return RetrievalMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "mode 只能为 SEMANTIC 或 VECTOR_RERANK");
        }
    }
}

