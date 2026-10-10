package com.weiguang.modules.appeal.dto;

import lombok.Data;

/**
 * POST /admin/feedbacks/{id}/reply 入参（详设 §3.4）：{conclusion}。
 */
@Data
public class FeedbackReplyRequest {

    private String conclusion;
}