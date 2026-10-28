package com.weiguang.modules.admin.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * GET /admin/overview 出参（详设 §3.4 / §6.3；v1.3 T25 增加趋势与安全统计）。
 */
@Data
public class AdminOverviewVO {

    private Long userCount;
    private Long orderTotal;
    private Long completedCount;
    /** HOME_VISIT/ACCOMPANY/REMOTE 三类型各计数 */
    private Map<String, Long> typeDistribution;

    /** 近 7 天趋势（按日期升序，含 0 值日期） */
    private List<DailyTrendVO> last7Days;

    /** 订单状态分布（PENDING/ACCEPTED/...） */
    private Map<String, Long> statusDistribution;

    /** 防诈高危上报总数（AI 命中） */
    private Long fraudReportCount;

    /** 社区待人工审核动态数 */
    private Long pendingManualPostCount;

    @Data
    public static class DailyTrendVO {
        /** yyyy-MM-dd */
        private String date;
        private Long orderCount;
        private Long completedCount;
        private Long userCount;
    }
}
