package com.weiguang.modules.incentive.dto;

import lombok.Data;

/**
 * S3 POST /api/orders/{id}/thank-letter 入参（详设 §2.14）。
 */
@Data
public class LetterRequest {

    /** 感谢信内容（≤500） */
    private String content;

    /** 是否用 AI 草稿起头（S2 点击"起个头"后由前端带 true；见《实现偏差清单》D23） */
    private Boolean aiAssisted;
}