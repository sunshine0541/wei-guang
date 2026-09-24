package com.weiguang.infra.interceptor;

import com.weiguang.common.error.ErrorCode;
import com.weiguang.modules.auth.service.JwtService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 用户端鉴权拦截器（详设 §3.1 账号状态拦截规则）：
 * CANCELLED（已注销）→ 一律 20001；BANNED（封禁）→ 读放行、写拦截 20004，
 * 唯一豁免：F1 提交申诉 /api/appeals、F2 提交反馈 /api/feedbacks。
 */
@Component
public class UserAuthInterceptor implements HandlerInterceptor {

    private static final String BANNED_MESSAGE = "您的账号暂时被限制了，如有疑问可在'反馈与申诉'中留言";

    /** 封禁用户仍可访问的写接口（详设 §3.1 唯一豁免） */
    private static final Set<String> BANNED_WRITE_EXEMPT = Set.of("/api/appeals", "/api/feedbacks");

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserMapper userMapper;

    public UserAuthInterceptor(JwtService jwtService, UserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        if (token == null) {
            AuthResponseWriter.write(response, ErrorCode.UNAUTHORIZED, ErrorCode.messageOf(ErrorCode.UNAUTHORIZED));
            return false;
        }

        Long uid;
        try {
            uid = jwtService.parseId(token, JwtService.IDENTITY_USER);
        } catch (ExpiredJwtException e) {
            AuthResponseWriter.write(response, ErrorCode.TOKEN_EXPIRED, ErrorCode.messageOf(ErrorCode.TOKEN_EXPIRED));
            return false;
        } catch (Exception e) {
            AuthResponseWriter.write(response, ErrorCode.UNAUTHORIZED, ErrorCode.messageOf(ErrorCode.UNAUTHORIZED));
            return false;
        }

        User user = userMapper.selectById(uid);
        if (user == null || "CANCELLED".equals(user.getStatus())) {
            AuthResponseWriter.write(response, ErrorCode.UNAUTHORIZED, ErrorCode.messageOf(ErrorCode.UNAUTHORIZED));
            return false;
        }
        if ("BANNED".equals(user.getStatus())
                && isWriteRequest(request)
                && !BANNED_WRITE_EXEMPT.contains(request.getRequestURI())) {
            AuthResponseWriter.write(response, ErrorCode.ACCOUNT_RESTRICTED, BANNED_MESSAGE);
            return false;
        }

        AuthContext.setUid(uid);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean isWriteRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
    }
}
