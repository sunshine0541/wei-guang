package com.weiguang.modules.incentive.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * S5 GET /api/orders/{id}/story-card 出参（详设 §3.2）。
 */
@Data
public class StoryCardVO {

    private String requesterName;
    private String helperName;
    private String thing;
    private String summary;
    private LocalDateTime createdAt;
}