package com.km.search.exception;

public enum ErrorCode {

    PARAM_INVALID(400001, "请求参数不合法"),
    INTERNAL_TOKEN_INVALID(401001, "内部服务 Token 不合法"),
    CHUNK_NOT_FOUND(404001, "切片不存在或不可检索"),
    AI_SERVICE_ERROR(502001, "AI 检索服务调用失败"),
    SYSTEM_ERROR(500001, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

