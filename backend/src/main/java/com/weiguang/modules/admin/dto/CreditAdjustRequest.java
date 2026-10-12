package com.weiguang.modules.admin.dto;

import lombok.Data;

/**
 * POST /admin/users/{id}/credit-adjust 入参（详设 §3.4）：{delta, remark}。
 */
@Data
public class CreditAdjustRequest {

    /** 微光值变化值（±），余额下限 0 收敛 */
    private Integer delta;
    private String remark;
}