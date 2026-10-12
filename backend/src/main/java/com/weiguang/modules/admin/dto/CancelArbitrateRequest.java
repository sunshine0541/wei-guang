package com.weiguang.modules.admin.dto;

import lombok.Data;

/**
 * POST /admin/orders/{id}/cancel-arbitrate 入参（详设 §3.4）：{decision:CANCEL|KEEP, conclusion}。
 */
@Data
public class CancelArbitrateRequest {

    /** CANCEL=裁决取消 / KEEP=维持原状态 */
    private String decision;
    private String conclusion;
}