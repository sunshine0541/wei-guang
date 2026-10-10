package com.weiguang.modules.appeal.dto;

import lombok.Data;

/**
 * F3 GET /api/appeals/mine 列表项（详设 §3.6）：
 * {id,type,orderId?,reason,status,conclusion?,createdAt,resolvedAt?}。
 */
@Data
public class AppealVO {

    private Long id;
    private String type;
    private Long orderId;
    private String reason;
    private String status;
    private String conclusion;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime resolvedAt;
}