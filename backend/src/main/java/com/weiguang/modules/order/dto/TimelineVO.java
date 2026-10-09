package com.weiguang.modules.order.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * O14 时间线单条（详设 §3.6：`[{eventType,operatorRole(SYSTEM/REQUESTER/HELPER/ADMIN),remark,createdAt}]`）。
 */
@Data
public class TimelineVO {

    private String eventType;
    private String operatorRole;
    private String remark;
    private LocalDateTime createdAt;
}