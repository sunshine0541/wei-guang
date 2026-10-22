package com.weiguang.infra.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.chat.dto.MessageVO;
import com.weiguang.modules.chat.dto.SendMessageRequest;
import com.weiguang.modules.chat.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WS 文本帧路由（详设 §3.5）：PING→PONG；CHAT_MESSAGE→走 ChatService（与 REST 同一条写路径）；
 * 鉴权失败 close 4401；发送异常以 SYSTEM 帧回执。
 */
@Slf4j
@Component
public class WeiguangWsHandler extends TextWebSocketHandler {

    private static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");

    private final WsSessionRegistry registry;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public WeiguangWsHandler(WsSessionRegistry registry, ChatService chatService, ObjectMapper objectMapper) {
        this.registry = registry;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object uid = session.getAttributes().get(WsHandshakeInterceptor.ATTR_UID);
        if (!(uid instanceof Long)) {
            session.close(UNAUTHORIZED);
            return;
        }
        registry.register((Long) uid, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        registry.touch(session);
        Object uid = session.getAttributes().get(WsHandshakeInterceptor.ATTR_UID);
        if (!(uid instanceof Long)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");
            if (WsEnvelope.TYPE_PING.equals(type)) {
                registry.sendTo(session, WsEnvelope.pong());
                return;
            }
            if (WsEnvelope.TYPE_CHAT_MESSAGE.equals(type)) {
                handleChat((Long) uid, session, root);
                return;
            }
            if (WsEnvelope.TYPE_TYPING.equals(type)) {
                chatService.notifyTyping((Long) uid, root.path("payload").path("sessionId").asLong());
            }
        } catch (Exception e) {
            log.warn("WS 帧处理失败：{}", e.getMessage());
        }
    }

    private void handleChat(Long uid, WebSocketSession session, JsonNode root) {
        JsonNode payload = root.path("payload");
        SendMessageRequest request = new SendMessageRequest();
        request.setClientMsgId(root.path("clientMsgId").asText(null));
        request.setMessageType(payload.path("messageType").asText(null));
        request.setContent(payload.path("content").asText(null));
        Long sessionId = payload.path("sessionId").asLong();
        try {
            // 发送成功回执由 ChatService 统一推送（REST/WS 同一条路径），此处不再重复回帧
            chatService.send(uid, sessionId, request);
        } catch (BizException e) {
            registry.sendTo(session, WsEnvelope.system(e.getCode(), e.getMessage(), request.getClientMsgId()));
        } catch (Exception e) {
            log.warn("WS 发送消息失败：{}", e.getMessage());
            registry.sendTo(session, WsEnvelope.system(50001, "系统繁忙，请稍后再试", request.getClientMsgId()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS 传输异常 sid={}：{}", session.getId(), exception.getMessage());
        registry.unregister(session);
    }
}