package com.weiguang.modules.appeal.dto;

import lombok.Data;

/**
 * F2 POST /api/feedbacks 入参（详设 §3.6）：{content} 必填 ≤500 字。
 */
@Data
public class FeedbackCreateRequest {

    private String content;
}