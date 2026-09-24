package com.weiguang.common.result;

import com.weiguang.common.error.ErrorCode;
import lombok.Data;

import java.util.UUID;

/**
 * 统一返回体 {code, message, data, traceId}（详设 §3.1）。
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok(T data) {
        return build(ErrorCode.SUCCESS, ErrorCode.messageOf(ErrorCode.SUCCESS), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code) {
        return build(code, ErrorCode.messageOf(code), null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(int code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        result.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        return result;
    }
}
