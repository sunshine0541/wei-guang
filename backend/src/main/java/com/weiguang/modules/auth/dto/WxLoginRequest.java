package com.weiguang.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A1 微信静默登录入参（详设 §3.3）。
 */
@Data
public class WxLoginRequest {

    @NotBlank(message = "参数校验失败")
    private String code;
}
