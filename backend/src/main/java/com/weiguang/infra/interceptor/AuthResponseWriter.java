package com.weiguang.infra.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 拦截器统一返回体写出（HTTP 200 + 业务错误码，与全局异常处理一致）。
 */
final class AuthResponseWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuthResponseWriter() {
    }

    static void write(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(Result.fail(code, message)));
    }
}
