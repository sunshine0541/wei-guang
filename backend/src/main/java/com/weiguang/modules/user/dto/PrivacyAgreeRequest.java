package com.weiguang.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A3 POST /api/user/privacy-agree 入参（详设 §3.3）。
 */
@Data
public class PrivacyAgreeRequest {

    @NotBlank(message = "参数校验失败")
    private String version;
}
