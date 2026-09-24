package com.weiguang.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息（表 chat_message，只增不改不删）。
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private Long sessionId;

    /** 系统消息 sender_id=0 */
    private Long senderId;

    /** TEXT/IMAGE/SYSTEM */
    private String messageType;

    /** 文本内容或图片 URL */
    private String content;

    /** 客户端幂等键；系统消息为 NULL */
    private String clientMsgId;

    /** NONE/HIGHLIGHT/STRONG */
    private String riskFlag;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
