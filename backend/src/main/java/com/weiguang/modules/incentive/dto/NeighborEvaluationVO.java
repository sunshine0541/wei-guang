package com.weiguang.modules.incentive.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * U12 GET /api/users/{id}/evaluations 单条（详设 §3.6）。
 */
@Data
public class NeighborEvaluationVO {

    private Integer stars;
    private String advice;
    private String requesterNickname;
    private LocalDateTime createdAt;
}