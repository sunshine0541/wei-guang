package com.weiguang.modules.admin.dto;

import lombok.Data;

import java.util.Map;

/**
 * GET /admin/overview 出参（详设 §3.4 / §6.3）：
 * 用户数、求助总数、已完成数、类型分布（三类型各计数）。
 */
@Data
public class AdminOverviewVO {

    private Long userCount;
    private Long orderTotal;
    private Long completedCount;
    /** HOME_VISIT/ACCOMPANY/REMOTE 三类型各计数 */
    private Map<String, Long> typeDistribution;
}