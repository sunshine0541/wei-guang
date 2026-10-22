package com.weiguang.modules.chat.dto;

import lombok.Data;

/**
 * C1 GET /api/orders/{id}/session 出参（详设 §3.2）。
 * 追加字段（需求方拍板，见《实现偏差清单》D10）：myUserId（消息归属渲染）、
 * addressArea/addressDetail（陪同集合点 / 上门类确认开始后解锁门牌）、
 * emergencyContact*（上门/陪同接单者、服务有效状态内）。
 */
@Data
public class SessionVO {

    private Long sessionId;
    private Integer round;
    private String status;
    private String peerNickname;
    private String peerAvatarUrl;
    private Integer myUnread;
    /** 当前登录用户 ID（前端据此区分"我的消息/对方消息"） */
    private Long myUserId;
    /** B：当前用户在本单中的角色 REQUESTER/HELPER（快捷短语按角色取） */
    private String myRole;
    /** 陪同类=集合点；上门类=小区/区域（接单后可见） */
    private String addressArea;
    /** 仅上门类且确认开始后解锁，服务结束即收回 */
    private String addressDetail;
    /** 仅上门/陪同类接单者、服务有效状态内可见 */
    private String emergencyContactName;
    private String emergencyContactPhone;
}