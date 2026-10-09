package com.weiguang.modules.incentive.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * U8/U9 账本明细单条（详设 §3.6）。
 */
@Data
public class LedgerRecordVO {

    private Integer delta;
    private String reasonCode;
    private Long orderId;
    private Integer balanceAfter;
    private String remark;
    private LocalDateTime createdAt;
}