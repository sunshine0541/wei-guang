package com.weiguang.modules.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * GET /admin/orders 列表项（详设 §3.4）。
 */
@Data
public class AdminOrderVO {

    private Long id;
    private String title;
    private String orderType;
    private String status;
    private Integer urgentFlag;
    private Long requesterId;
    private String requesterNickname;
    private Long helperId;
    private String helperNickname;
    /** 协商取消发起方（非空=挂起单） */
    private String cancelPendingBy;
    private LocalDateTime createdAt;
}