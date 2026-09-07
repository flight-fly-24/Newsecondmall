package com.example.mall.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * 统一 API 返回结构，便于 Web / 小程序 / App 共用一套解析逻辑。
 */
public class ApiResult<T> {

    private final int code;
    private final String message;
    private final T data;

    @JsonCreator
    public ApiResult(@JsonProperty("code") int code,
                     @JsonProperty("message") String message,
                     @JsonProperty("data") T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
