package com.weiguang.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * U3 出参（详设 §3.6）：切换模式后动效重置为该模式默认值。
 */
@Data
@AllArgsConstructor
public class ModeVO {

    private String uiMode;
    private Integer motionEnabled;
}
