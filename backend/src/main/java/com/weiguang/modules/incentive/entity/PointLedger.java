package com.weiguang.modules.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分明细（表 point_ledger，只增不改不删）。
 */
@Data
@TableName("point_ledger")
public class PointLedger {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** MVP 仅 +10 */
    private Integer delta;

    /** COMPLETE */
    private String reasonCode;

    /** 关联订单 ID */
    private Long orderId;

    /** ORDER_COMPLETE_<order_id> 防重复发放 */
    private String uniqueKey;

    /** 余额快照 */
    private Integer balanceAfter;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
