package com.km.admin.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理：把业务异常/校验异常包装成统一 ApiResponse。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBadRequest(IllegalArgumentException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.fail(1001, e.getMessage() == null ? "参数校验失败" : e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleConflict(IllegalStateException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponse.fail(2004, e.getMessage() == null ? "业务状态不允许" : e.getMessage()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBind(BindException e) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("fieldErrors", e.getFieldErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.fail(1001, "参数校验失败"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.fail(1001, "参数校验失败"));
    }

    /**
     * F2 commit #28 引入：BusinessException 携带错误码，按业务语义映射 HTTP 状态。
     * - 1001/2001/2002 → 400
     * - 2004/3001     → 409
     * - 2005          → 409（在途任务）
     * - 5001          → 500
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBusiness(BusinessException e) {
        int code = e.getCode();
        HttpStatus status;
        if (code == 5001) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (code == 2004 || code == 2005 || code == 3001) {
            status = HttpStatus.CONFLICT;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(ApiResponse.fail(code, e.getMessage()));
    }
}
