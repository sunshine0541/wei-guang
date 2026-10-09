package com.weiguang.modules.incentive.dto;

import lombok.Data;

/**
 * S1 POST /api/orders/{id}/evaluation 入参（详设 §2.13、§3.3）。
 */
@Data
public class EvaluationRequest {

    /** 星级 1–5，必填 */
    private Integer stars;

    /** 可选一句建议（≤100） */
    private String advice;
}