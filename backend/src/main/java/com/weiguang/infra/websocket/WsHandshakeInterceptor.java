package com.weiguang.infra.websocket;

import com.weiguang.modules.auth.service.JwtService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WS 握手鉴权（详设 §3.5）：`ws://{host}/ws?token=<JWT>`。
 * 解析 token 放入 attributes；失败不放 uid，由 handler 以 close code 4401 关闭。
 */
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_UID = "uid";

    private final JwtService jwtService;
    private final UserMapper userMapper;

    public WsHandshakeInterceptor(JwtService jwtService, UserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token != null && !token.isBlank()) {
            try {
                Long uid = jwtService.parseId(token, JwtService.IDENTITY_USER);
                User user = userMapper.selectById(uid);
                if (user != null && !"CANCELLED".equals(user.getStatus())) {
                    attributes.put(ATTR_UID, uid);
                }
            } catch (Exception ignored) {
                // 校验失败不抛：统一由 handler 以 4401 关闭（详设 §3.5）
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}