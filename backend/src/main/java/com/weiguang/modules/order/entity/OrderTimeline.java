package com.weiguang.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单时间线（表 order_timeline，只增不改不删）。
 */
@Data
@TableName("order_timeline")
public class OrderTimeline {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** PUBLISH/ACCEPT/START/RELEASE/CANCEL/CANCEL_REQUEST/CANCEL_CONFIRM_REJECT/COMPLETE_REQUEST/CONFIRM/COMPLETE/EXPIRE/APPEAL_OPEN/ARBITRATE/ADMIN_ACTION/REPUBLISH */
    private String eventType;

    /** 0=系统 */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
