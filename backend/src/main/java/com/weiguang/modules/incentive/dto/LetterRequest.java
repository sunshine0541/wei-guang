package com.weiguang.modules.incentive.dto;

import lombok.Data;

/**
 * S3 POST /api/orders/{id}/thank-letter 入参（详设 §2.14）。
 */
@Data
public class LetterRequest {

    /** 感谢信内容（≤500） */
    private String content;
}