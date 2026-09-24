package com.weiguang.infra.interceptor;

import com.weiguang.common.error.ErrorCode;
import com.weiguang.modules.admin.entity.AdminUser;
import com.weiguang.modules.admin.mapper.AdminUserMapper;
import com.weiguang.modules.auth.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端鉴权拦截器（详设 §3.1）：/admin/** 独立 JWT，claim identity=admin，与用户端完全隔离。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AdminUserMapper adminUserMapper;

    public AdminAuthInterceptor(JwtService jwtService, AdminUserMapper adminUserMapper) {
        this.jwtService = jwtService;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        if (token == null) {
            AuthResponseWriter.write(response, ErrorCode.UNAUTHORIZED, ErrorCode.messageOf(ErrorCode.UNAUTHORIZED));
            return false;
        }

        Long adminId;
        try {
            adminId = jwtService.parseId(token, JwtService.IDENTITY_ADMIN);
        } catch (ExpiredJwtException e) {
            AuthResponseWriter.write(response, ErrorCode.TOKEN_EXPIRED, ErrorCode.messageOf(ErrorCode.TOKEN_EXPIRED));
            return false;
        } catch (Exception e) {
            AuthResponseWriter.write(response, ErrorCode.UNAUTHORIZED, ErrorCode.messageOf(ErrorCode.UNAUTHORIZED));
            return false;
        }

        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null || !"NORMAL".equals(admin.getStatus())) {
            AuthResponseWriter.write(response, ErrorCode.FORBIDDEN, ErrorCode.messageOf(ErrorCode.FORBIDDEN));
            return false;
        }

        AuthContext.setAdminId(adminId);
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
}
