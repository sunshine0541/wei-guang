package com.weiguang.infra.websocket;

import com.weiguang.modules.chat.dto.MessageVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WS 信封（详设 §3.5）：{type, payload, ts, clientMsgId}。
 * type 枚举：CHAT_MESSAGE / ORDER_STATUS / SYSTEM / PING / PONG。
 */
public final class WsEnvelope {

    public static final String TYPE_CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final String TYPE_ORDER_STATUS = "ORDER_STATUS";
    public static final String TYPE_SYSTEM = "SYSTEM";
    public static final String TYPE_PING = "PING";
    public static final String TYPE_PONG = "PONG";

    private WsEnvelope() {
    }

    public static Map<String, Object> of(String type, Object payload, String clientMsgId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("payload", payload);
        envelope.put("ts", System.currentTimeMillis());
        envelope.put("clientMsgId", clientMsgId);
        return envelope;
    }

    /** 下行聊天消息（详设 §3.5：服务端下行消息带 msgId） */
    public static Map<String, Object> chatMessage(Long sessionId, MessageVO message, String clientMsgId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("msgId", message.getMsgId());
        payload.put("senderId", message.getSenderId());
        payload.put("messageType", message.getMessageType());
        payload.put("content", message.getContent());
        payload.put("riskFlag", message.getRiskFlag());
        payload.put("createdAt", message.getCreatedAt());
        return of(TYPE_CHAT_MESSAGE, payload, clientMsgId);
    }

    /** 系统提示（如发送失败回执 / 防诈强提示回执） */
    public static Map<String, Object> system(int code, String message) {
        return system(code, message, null);
    }

    /** 系统提示（带 clientMsgId：发送失败时前端据此标记对应气泡） */
    public static Map<String, Object> system(int code, String message, String clientMsgId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        return of(TYPE_SYSTEM, payload, clientMsgId);
    }

    /** 订单状态变更推送（详设 §4.1 副作用；各流转落地时调用） */
    public static Map<String, Object> orderStatus(Long orderId, String status, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("status", status);
        payload.put("title", title);
        return of(TYPE_ORDER_STATUS, payload, null);
    }

    /** 心跳应答 */
    public static Map<String, Object> pong() {
        return of(TYPE_PONG, null, null);
    }
}