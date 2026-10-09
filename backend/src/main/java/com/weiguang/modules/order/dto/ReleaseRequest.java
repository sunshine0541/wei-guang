package com.weiguang.modules.order.dto;

import lombok.Data;

/**
 * O8 POST /api/orders/{id}/release 入参（详设 §3.3）。
 */
@Data
public class ReleaseRequest {

    /** HELPER_QUIT 主动放弃 / HELPLESS 实在帮不上 / HELPER_LOST 服务中失联或退出 */
    private String reasonType;

    /** 理由（HELPLESS 必填） */
    private String reasonText;
}