package com.weiguang.modules.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知（表 notification）。
 */
@Data
@TableName("notification")
public class Notification {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 接收用户 ID */
    private Long userId;

    /** NEW_MESSAGE/ACCEPTED/SERVICE_STARTED/CONFIRM_REQUEST/SETTLED/RELEASED/EXPIRED/APPEAL_RESULT/COOLDOWN/POINTS_CREDITED/CREDIT_CHANGED/ADMIN_ACTION/FEEDBACK_RESULT */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** ORDER/SESSION/APPEAL/NONE */
    private String jumpType;

    /** 跳转目标 ID */
    private Long jumpId;

    /** 是否已读 */
    private Integer isRead;
    /** 聊天类聚合通知的未读条数（非聊天类固定 1） */
    private Integer messageCount;
    /** 聊天类通知的发送方昵称 */
    private String senderNickname;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
