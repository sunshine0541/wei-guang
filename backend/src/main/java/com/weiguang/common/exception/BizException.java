package com.weiguang.common.exception;

import com.weiguang.common.error.ErrorCode;

/**
 * 业务异常：由全局异常处理器转换为对应错误码返回。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code) {
        super(ErrorCode.messageOf(code));
        this.code = code;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
