package com.km.admin.model.vo;

public class ApiResult<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<T>();
        result.setCode(0);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> ApiResult<T> error(Integer code, String message) {
        ApiResult<T> result = new ApiResult<T>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(null);
        return result;
    }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
