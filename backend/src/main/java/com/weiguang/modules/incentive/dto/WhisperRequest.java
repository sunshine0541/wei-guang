package com.weiguang.modules.incentive.dto;

import lombok.Data;

/**
 * S4 POST /api/orders/{id}/whisper 入参（详设 §2.17）。
 */
@Data
public class WhisperRequest {

    /** 悄悄话内容（≤200） */
    private String content;
}