package com.weiguang.modules.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * U4 PUT /api/user/motion 入参（详设 §3.6）。
 */
@Data
public class MotionRequest {

    @NotNull(message = "参数校验失败")
    private Integer enabled;
}
