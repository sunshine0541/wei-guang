package com.weiguang.modules.order.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * O5 我的求助列表项（详设 §3.6）。
 */
@Data
public class MyOrderVO {

    private Long id;
    private String orderType;
    private String title;
    private List<String> tags;
    private String status;
    private Integer urgentFlag;
    private Integer viewCount;
    private Integer currentRound;
    /** 当前轮接单者昵称，未接单为 null */
    private String helperNickname;
    /** 发布者昵称——仅 tab=HELPING（我接的单）时返回，供接单者识别是谁的求助（见《实现偏差清单》D16） */
    private String requesterNickname;
    /** 申诉截止——P17 关联求助选择器据此过滤可申诉范围（见《实现偏差清单》D17） */
    private LocalDateTime appealDeadline;
    private LocalDateTime createdAt;
}
