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
    /** B：自定义标签（仅展示，不入推荐） */
    private String extraTag;
    private Integer urgentFlag;
    private String timeExpect;
    private Integer viewCount;
    private LocalDateTime createdAt;
    /** B：同区域距离粗算用（不暴露楼栋门牌） */
    private String addressArea;
    /** 4D：距本人默认地址的直线距离（公里，服务端计算；无坐标/远程/本人单为 null） */
    private Double distanceKm;
    private RequesterVO requester;
    /** 是否本人发布（本人单展示但接单按钮置灰） */
    private boolean mine;
}
