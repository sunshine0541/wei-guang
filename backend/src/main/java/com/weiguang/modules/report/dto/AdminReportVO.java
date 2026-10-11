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
    private Long targetUserId;
    private String type;
    private String evidence;
    private String status;
    private String action;
    private String handleNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}