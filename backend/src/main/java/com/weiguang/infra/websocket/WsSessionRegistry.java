package com.weiguang.infra.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WS 会话注册表（详设 §3.5）：uid → 连接；75s 未收到任何帧关闭；向在线用户推送信封。
 */
@Slf4j
@Component
public class WsSessionRegistry {

    /** 服务端 75s 未收到任何帧关闭连接（详设 §3.5） */
    private static final long IDLE_TIMEOUT_MS = 75_000L;
    /** 空闲扫描间隔 */
    private static final long SWEEP_INTERVAL_MS = 30_000L;

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> lastActiveAt = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WsSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long uid, WebSocketSession session) {
        sessions.put(uid, session);
        lastActiveAt.put(session.getId(), System.currentTimeMillis());
    }

    public void unregister(WebSocketSession session) {
        sessions.values().removeIf(item -> item.getId().equals(session.getId()));
        lastActiveAt.remove(session.getId());
    }

    /** 收到任何帧即刷新活跃时间 */
    public void touch(WebSocketSession session) {
        lastActiveAt.put(session.getId(), System.currentTimeMillis());
    }

    public boolean isOnline(Long uid) {
        WebSocketSession session = sessions.get(uid);
        return session != null && session.isOpen();
    }

    /** 向指定用户推送信封；失败视为离线，由调用方决定是否落通知 */
    public boolean push(Long uid, Object envelope) {
        WebSocketSession session = sessions.get(uid);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.warn("WS 推送失败 uid={}：{}", uid, e.getMessage());
            unregister(session);
            return false;
        }
    }

    /** 异步推送（T12 通知中心：不动用业务线程，慢连接不阻塞主流程） */
    @Async
    public void pushAsync(Long uid, Object envelope) {
        push(uid, envelope);
    }

    /** 直接回帧（如 PONG） */
    public void sendTo(WebSocketSession session, Object envelope) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("WS 回帧失败 sid={}：{}", session.getId(), e.getMessage());
        }
    }

    /** 空闲扫描：75s 未收到任何帧 → 关闭 */
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    public void sweepIdle() {
        long now = System.currentTimeMillis();
        sessions.values().forEach(session -> {
            Long activeAt = lastActiveAt.get(session.getId());
            if (activeAt != null && now - activeAt > IDLE_TIMEOUT_MS) {
                try {
                    session.close(new CloseStatus(4408, "idle timeout"));
                } catch (IOException e) {
                    log.warn("WS 空闲关闭失败 sid={}：{}", session.getId(), e.getMessage());
                }
                unregister(session);
            }
        });
    }
}