package com.weiguang.modules.notify.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * N1 通知列表项（详设 §3.6）：{id,type,title,content,jumpType,jumpId,isRead,createdAt}。
 */
@Data
public class NotificationVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private String jumpType;
    private Long jumpId;
    private Integer isRead;
    /** B：聊天类聚合通知的未读条数 */
    private Integer messageCount;
    /** B：聊天类通知的发送方昵称 */
    private String senderNickname;
    private LocalDateTime createdAt;
}