package com.weiguang.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话（表 chat_session，一个会话只服务一轮接单）。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 轮次 */
    private Integer round;

    /** 求助者 ID */
    private Long requesterId;

    /** 助人者 ID */
    private Long helperId;

    /** ACTIVE/CLOSED */
    private String status;

    /** 求助者未读数 */
    private Integer requesterUnread;

    /** 助人者未读数 */
    private Integer helperUnread;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;
}
