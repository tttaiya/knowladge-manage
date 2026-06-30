package com.km.admin.exception;

import com.km.admin.model.vo.ApiResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Object> handleBusinessException(BusinessException ex) {
        return ApiResult.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        return ApiResult.error(4000, fieldError == null ? "???????" : fieldError.getDefaultMessage());
    }

    @ExceptionHandler(BindException.class)
    public ApiResult<Object> handleBindException(BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        return ApiResult.error(4000, fieldError == null ? "???????" : fieldError.getDefaultMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<Object> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        return ApiResult.error(4000, "??????: " + ex.getParameterName());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Object> handleException(Exception ex) {
        return ApiResult.error(5000, ex.getMessage() == null ? "????" : ex.getMessage());
    }
}
