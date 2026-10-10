package com.weiguang.modules.appeal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * GET /admin/appeals 列表项（详设 §3.4）。
 */
@Data
public class AdminAppealVO {

    private Long id;
    private String type;
    private Long orderId;
    /** 关联求助标题（FEEDBACK 为 null） */
    private String orderTitle;
    private Long initiatorId;
    private String initiatorNickname;
    private String reason;
    private String status;
    private String result;
    private String conclusion;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}