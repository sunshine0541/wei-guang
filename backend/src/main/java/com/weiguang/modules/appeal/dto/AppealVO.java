package com.weiguang.modules.appeal.dto;

import lombok.Data;

/**
 * F3 GET /api/appeals/mine 列表项（详设 §3.6）：
 * {id,type,orderId?,reason,status,conclusion?,createdAt,resolvedAt?}。
 */
@Data
public class AppealVO {

    private Long id;
    private String type;
    private Long orderId;
    /** 关联求助标题（反馈为 null）——P17 列表需展示"针对哪件事"（见《实现偏差清单》D20） */
    private String orderTitle;
    private String reason;
    private String status;
    private String conclusion;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime resolvedAt;
}