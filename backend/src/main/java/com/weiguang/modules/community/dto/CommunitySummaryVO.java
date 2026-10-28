package com.weiguang.modules.community.dto;

import com.weiguang.modules.incentive.dto.HonorItemVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CM1 社区顶部三块聚合（详设 §15.1.4）。
 */
@Data
public class CommunitySummaryVO {

    /** 助人之星 TOP10（复用荣耀榜口径） */
    private List<HonorItemVO> stars;

    /** 今日新求助（当日 0 点后创建的 PENDING 单数） */
    private Long todayOrderCount;

    /** 今日新求助，最多 3 条（列表展示用） */
    private List<TodayOrderVO> todayOrders;

    /** 已成为微光人数（light_state=STEADY 且 ACTIVE） */
    private Long microLightCount;

    @Data
    public static class TodayOrderVO {
        private Long id;
        private String title;
        private String orderType;
        private Integer urgentFlag;
        private LocalDateTime createdAt;
    }
}
