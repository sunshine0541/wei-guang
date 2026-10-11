package com.weiguang.modules.incentive.dto;

import lombok.Data;

import java.util.List;

/**
 * H1 GET /api/home/summary 出参（详设 §3.6）：荣耀榜 TOP10 + 善意回流计数。
 */
@Data
public class HomeSummaryVO {

    /** 荣耀榜 TOP10（近 30 天完成次数降序，并列按最近完成时间早者优先） */
    private List<HonorItemVO> honorTop10;

    /** 善意回流计数 = light_passed=1 的用户数（注销用户不计） */
    private Long lightPassedCount;
}