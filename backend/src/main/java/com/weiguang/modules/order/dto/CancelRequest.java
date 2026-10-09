package com.weiguang.modules.order.dto;

import lombok.Data;

/**
 * O9 POST /api/orders/{id}/cancel 入参（详设 §3.3；`mode` 区分单方取消与发起协商，见《实现偏差清单》D13）。
 */
@Data
public class CancelRequest {

    /** 取消理由（必填） */
    private String reason;

    /** CANCEL 单方取消（默认）/ NEGOTIATE 发起协商取消 */
    private String mode;
}