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
    private LocalDateTime createdAt;
}
