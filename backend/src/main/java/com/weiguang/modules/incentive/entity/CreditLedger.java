package com.weiguang.modules.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微光值明细（表 credit_ledger，只增不改不删）。
 */
@Data
@TableName("credit_ledger")
public class CreditLedger {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 变化值（±） */
    private Integer delta;

    /** COMPLETE/ACCEPT_CANCEL/START_TIMEOUT/MID_QUIT/URGENT_ABUSE/MALICIOUS_CANCEL/FAKE_ORDER/APPEAL_LOSS/ADMIN_ADJUST/APPEAL_REVERT */
    private String reasonCode;

    /** 关联订单 ID */
    private Long orderId;

    /** 余额快照（下限 0 收敛） */
    private Integer balanceAfter;

    /** 0=系统；管理员处置时为 admin_user.id */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
