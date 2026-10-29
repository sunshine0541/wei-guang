package com.weiguang.modules.report.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端上报列表项（详设 §3.4 GET /admin/reports）。
 */
@Data
public class AdminReportVO {

    private Long id;
    private Long orderId;
    private Long messageId;
    private String targetType;
    private Long targetId;
    /** 内容举报：帖子 ID（评论举报时为评论所在帖子） */
    private Long targetPostId;
    /** 内容举报：帖子标题/内容摘要/状态，便于管理端不跳页判断 */
    private String targetTitle;
    private String targetContent;
    private String targetStatus;
    private Long targetUserId;
    private Long reporterId;
    private String type;
    private String evidence;
    private String status;
    private String action;
    private String handleNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}