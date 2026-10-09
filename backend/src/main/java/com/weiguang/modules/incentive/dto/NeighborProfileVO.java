package com.weiguang.modules.incentive.dto;

import lombok.Data;

import java.util.List;

/**
 * U11 GET /api/users/{id}/profile 出参（详设 §3.6：不含年龄/地址/小习惯/紧急联系人/微光值）。
 */
@Data
public class NeighborProfileVO {

    private String nickname;
    private String avatarUrl;
    private String signature;
    /** 评价均值（无评价时 null） */
    private Double evaluationAvg;
    private Long evaluationCount;
    private List<ThankVO> thanks;
}