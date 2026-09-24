package com.weiguang.modules.order.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * O3 求助列表项（详设 §3.3：**禁止包含 age_priority 与发布者年龄**）。
 */
@Data
public class OrderCardVO {

    private Long id;
    private String title;
    private String orderType;
    private List<String> tags;
    private Integer urgentFlag;
    private String timeExpect;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private RequesterVO requester;
    /** 是否本人发布（本人单展示但接单按钮置灰） */
    private boolean mine;
}
