package com.weiguang.modules.chat.dto;

import lombok.Data;

/**
 * C3 POST /api/sessions/{id}/messages 入参（详设 §3.3）。
 */
@Data
public class SendMessageRequest {

    /** 客户端幂等键（唯一键去重） */
    private String clientMsgId;

    /** TEXT / IMAGE */
    private String messageType;

    /** 文本内容（≤`content.chat_text_max_length`）或已上传图片 URL */
    private String content;
}