package com.weiguang.modules.order.dto;

import lombok.Data;

/**
 * O12 POST /api/orders/{id}/confirm 入参（详设 §3.3）。
 */
@Data
public class ConfirmRequest {

    /** true 确认完成（结算）；false 拒绝确认（进入申诉中，reason 必填） */
    private Boolean agree;

    /** 拒绝理由（agree=false 时必填，≤200） */
    private String reason;
}