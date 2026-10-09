package com.weiguang.modules.chat.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * C2/C3 消息出参（详设 §3.2）。
 */
@Data
public class MessageVO {

    private Long msgId;
    private Long senderId;
    private String messageType;
    private String content;
    private String riskFlag;
    private LocalDateTime createdAt;
}