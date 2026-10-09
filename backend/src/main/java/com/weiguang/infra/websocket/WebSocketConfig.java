package com.weiguang.infra.websocket;

import com.weiguang.modules.auth.service.JwtService;
import com.weiguang.modules.user.mapper.UserMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WS 端点注册（详设 §3.5）：/ws?token=。走独立拦截器，不受 /api/** 鉴权影响。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WeiguangWsHandler wsHandler;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public WebSocketConfig(WeiguangWsHandler wsHandler, JwtService jwtService, UserMapper userMapper) {
        this.wsHandler = wsHandler;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsHandler, "/ws")
                .addInterceptors(new WsHandshakeInterceptor(jwtService, userMapper))
                .setAllowedOriginPatterns("*");
    }
}