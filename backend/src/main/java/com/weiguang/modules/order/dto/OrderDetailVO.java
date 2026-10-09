package com.weiguang.modules.order.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * O4 求助详情（详设 §3.3）：地址分级——未接单/浏览者仅返回 addressArea，远程类不返回任何地址，detail 永不返回。
 */
@Data
public class OrderDetailVO {

    private Long id;
    private String title;
    private String orderType;
    private List<String> tags;
    private Integer urgentFlag;
    private String description;
    private String timeExpect;
    private LocalDateTime createdAt;
    private Integer viewCount;
    /** 订单状态（详设未枚举 O4 响应字段；P04 据此渲染"我愿意帮助"等状态动作，见《实现偏差清单》D07） */
    private String status;
    private RequesterVO requester;
    /** 仅当浏览者不是发布者时返回小区/区域；远程类为 null */
    private String addressArea;
    private boolean mine;
    /** 发布者视图附加文案："已有 N 位邻居看过您的求助" */
    private String viewTip;
}
