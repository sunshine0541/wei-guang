package com.weiguang.modules.order.dto;

import lombok.Data;

/**
 * O10 POST /api/orders/{id}/cancel-confirm 入参（详设 §3.3）。
 */
@Data
public class CancelConfirmRequest {

    /** true 同意协商取消（→ CANCELLED）；false 拒绝（清空挂起并记录时间线） */
    private Boolean agree;
}