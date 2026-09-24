package com.weiguang.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * U3 PUT /api/user/mode 入参（详设 §3.6）。
 */
@Data
public class ModeRequest {

    @NotBlank(message = "参数校验失败")
    private String uiMode;
}
