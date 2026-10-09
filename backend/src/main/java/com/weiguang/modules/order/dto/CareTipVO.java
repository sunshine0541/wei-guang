package com.weiguang.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * O15 照护提示卡出参（详设 §3.3：{nickname（称呼）, careTip}）。
 */
@Data
@AllArgsConstructor
public class CareTipVO {

    private String nickname;
    private String careTip;
}