package com.weiguang.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * U5 POST /api/user/light-invite 入参（详设 §3.3）。
 */
@Data
public class LightInviteRequest {

    @NotBlank(message = "参数校验失败")
    private String choice;
}
